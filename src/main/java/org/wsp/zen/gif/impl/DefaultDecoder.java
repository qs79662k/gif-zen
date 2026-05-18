package org.wsp.zen.gif.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.wsp.zen.cache.core.Cache;
import org.wsp.zen.cache.core.CacheManager;
import org.wsp.zen.cache.core.RemovalListener;
import org.wsp.zen.gif.core.Decoder;
import org.wsp.zen.gif.core.EvictionPolicyFactory;
import org.wsp.zen.gif.core.LzwDecompressor;
import org.wsp.zen.gif.core.LzwDecompressorFactory;
import org.wsp.zen.gif.core.ParseCallback;
import org.wsp.zen.gif.core.Parser;
import org.wsp.zen.gif.core.Renderer;
import org.wsp.zen.gif.core.RendererFactory;
import org.wsp.zen.gif.core.SourceResolver;
import org.wsp.zen.gif.exception.DecoderException;
import org.wsp.zen.gif.extension.ApplicationExtension;
import org.wsp.zen.gif.extension.CommentExtension;
import org.wsp.zen.gif.extension.Extension;
import org.wsp.zen.gif.extension.ExtensionContainer;
import org.wsp.zen.gif.extension.PlainTextExtension;
import org.wsp.zen.gif.model.DecodeFrame;
import org.wsp.zen.gif.model.DisplayMode;
import org.wsp.zen.gif.model.EvictionPolicyContext;
import org.wsp.zen.gif.model.FrameData;
import org.wsp.zen.gif.model.FrameInfo;
import org.wsp.zen.gif.model.CacheNames;
import org.wsp.zen.gif.model.Header;
import org.wsp.zen.gif.model.PoolNames;
import org.wsp.zen.gif.util.CacheWindowHelper;
import org.wsp.zen.gif.util.CloseState;
import org.wsp.zen.gif.util.DirectionHelper;
import org.wsp.zen.gif.util.PoolUtils;
import org.wsp.zen.handler.core.Handler;
import org.wsp.zen.handler.core.HandlerFactory;
import org.wsp.zen.handler.model.HandlerContext;
import org.wsp.zen.mapping.core.FileMappingManager;
import org.wsp.zen.mapping.core.FileMappingManagerFactory;
import org.wsp.zen.mapping.core.MappingDirection;
import org.wsp.zen.mapping.impl.DefaultFileMappingManagerFactory;
import org.wsp.zen.mapping.model.MappingContext;
import org.wsp.zen.pool.core.ObjectPool;
import org.wsp.zen.pool.core.PoolManager;

/**
 * 默认 GIF 解码器实现，采用三级流水线（读取 → 解码 → 渲染）和双方向缓存窗口。
 * <p>
 * 支持通过 {@link Builder} 注入自定义组件。对象池支持通过 {@link PoolManager}
 * 统一注入；若管理器为 {@code null}，则完全不使用对象池。启用时必须同时
 * 提供渲染结果类型令牌 {@link Builder#withRenderImageType(Class)}。
 * </p>
 *
 * <p><b>资源关闭策略：</b>
 * <ul>
 *   <li><b>缓存管理器（CacheManager）</b> —— 由解码器自身在 {@link #close()} 时负责清空并关闭。
 *       解码器独占缓存实例，加载新文件时清空数据但不会关闭实例本身。</li>
 *   <li><b>线程池</b> —— 外部注入的线程池由调用者自行关闭，解码器内部创建的线程池由
 *       解码器在 {@link #close()} 时关闭。</li>
 *   <li><b>对象池管理器（PoolManager）</b> —— 虽然作为组件被解码器独占使用，
 *       但其内部管理的各个对象池（如 byte[]、int[] 数组池）可能被解码器之外的
 *       其他组件所共享，因此 PoolManager 本身的所有权依然属于上层调用者，
 *       解码器绝不会关闭传入的 PoolManager，关闭职责由上层调用者负责。</li>
 *   <li><b>渲染图像池（{@value PoolNames#RENDER_IMAGE}）</b> —— 是 PoolManager 内部
 *       唯一由解码器自行维护生命周期的对象池。解码器在加载新文件、重置和关闭时
 *       会清空该池，并在关闭时从 PoolManager 中移除该池的注册，但不会关闭
 *       PoolManager 本身。</li>
 *   <li>通过 {@link #load(InputStream)} 传入的流，解码器会在解析完成或出错时自动关闭，
 *       调用者无需处理。</li>
 * </ul>
 *
 * <p>所有公开方法均用 {@code synchronized} 保证线程安全。</p>
 *
 * @param <T> 渲染结果的类型（例如 BufferedImage、Bitmap 等）
 * @author wsp
 * @version 1.8
 */
public class DefaultDecoder<T> implements Decoder<T> {

    // ==================== 核心组件 ====================
    private final Parser parser;
    private final LzwDecompressorFactory lzwDecompressorFactory;
    private final LzwDecompressor lzwDecompressor;
    private final RendererFactory<T> rendererFactory;
    private final HandlerFactory<T> handlerFactory;
    private final FileMappingManagerFactory mappingManagerFactory;
    private final SourceResolver sourceResolver;

    private FileMappingManager mappingManager;
    private Handler<FrameData> readHandler;
    private Handler<DecodeFrame> decodeHandler;
    private Handler<T> renderHandler;

    // ==================== 对象池与缓存 ====================
    private final PoolManager poolManager;
    private final CacheManager cacheManager;
    private ObjectPool<T> renderImagePool;
    private final Class<T> renderImageType;

    // ==================== 线程池 ====================
    private final ExecutorService readExecutor;
    private final ExecutorService decodeExecutor;
    private final ExecutorService renderExecutor;
    private final ExecutorService wraparoundExecutor;
    private final boolean ownReadExecutor;
    private final boolean ownDecodeExecutor;
    private final boolean ownRenderExecutor;
    private final boolean ownWraparoundExecutor;

    // ==================== 状态 ====================
    private boolean loadComplete = false;
    private int lastRequested = -1;
    private boolean streamMode = false;
    private boolean lastDirection = true;
    private final CloseState closeState = new CloseState("解码器");

    // ==================== 数据容器 ====================
    private Header header;
    private final List<FrameInfo> frameInfos = Collections.synchronizedList(new ArrayList<>());
    private final ExtensionContainer extensionContainer = new ExtensionContainer();

    // ==================== 配置 ====================
    private final int minFrames;
    private final long loadTimeout;
    private final int forwardWindowSize;
    private final int backwardWindowSize;
    private final int mappingWindowSize;

    // ==================== 固定的移除监听器（构造时一次性绑定） ====================
    private final RemovalListener<Integer, FrameData> frameDataRemovalListener;
    private final RemovalListener<Integer, DecodeFrame> decodeFrameRemovalListener;
    private final RemovalListener<Integer, T> renderFrameRemovalListener;

    // ==================== 驱逐策略工厂 ====================
    private final EvictionPolicyFactory<Integer> frameDataEvictionFactory;
    private final EvictionPolicyFactory<Integer> decodedFrameEvictionFactory;
    private final EvictionPolicyFactory<Integer> renderedFrameEvictionFactory;

    // ==================== 任务引用 ====================
    private final AtomicReference<Future<?>> currentTaskFuture = new AtomicReference<>();

    // ===========================================================================
    // Builder
    // ===========================================================================

    public static class Builder<T> {
        private Parser parser;
        private LzwDecompressorFactory lzwDecompressorFactory = new DefaultLzwDecompressorFactory();
        private FileMappingManagerFactory mappingManagerFactory = new DefaultFileMappingManagerFactory();

        private RendererFactory<T> rendererFactory;
        private HandlerFactory<T> handlerFactory;

        private ExecutorService readExecutor;
        private ExecutorService decodeExecutor;
        private ExecutorService renderExecutor;
        private ExecutorService wraparoundExecutor;

        private int minFrames = 1;
        private long loadTimeout = 5;
        private int forwardWindowSize = 2;
        private int backwardWindowSize = 4;
        private int mappingWindowSize = 1024 * 1024 * 5;

        private EvictionPolicyFactory<Integer> frameDataEvictionFactory;
        private EvictionPolicyFactory<Integer> decodedFrameEvictionFactory;
        private EvictionPolicyFactory<Integer> renderedFrameEvictionFactory;

        private PoolManager poolManager;
        private CacheManager cacheManager;
        private SourceResolver sourceResolver = new DefaultSourceResolver();
        private Class<T> renderImageType;

        public Builder<T> withParser(Parser parser) { this.parser = Objects.requireNonNull(parser); return this; }
        public Builder<T> withLzwDecompressorFactory(LzwDecompressorFactory f) { this.lzwDecompressorFactory = Objects.requireNonNull(f); return this; }
        public Builder<T> withMappingManagerFactory(FileMappingManagerFactory f) { this.mappingManagerFactory = Objects.requireNonNull(f); return this; }
        public Builder<T> withRendererFactory(RendererFactory<T> f) { this.rendererFactory = Objects.requireNonNull(f); return this; }
        public Builder<T> withHandlerFactory(HandlerFactory<T> f) { this.handlerFactory = Objects.requireNonNull(f); return this; }
        public Builder<T> withReadExecutor(ExecutorService e) { this.readExecutor = Objects.requireNonNull(e); return this; }
        public Builder<T> withDecodeExecutor(ExecutorService e) { this.decodeExecutor = Objects.requireNonNull(e); return this; }
        public Builder<T> withRenderExecutor(ExecutorService e) { this.renderExecutor = Objects.requireNonNull(e); return this; }
        public Builder<T> withWraparoundExecutor(ExecutorService e) { this.wraparoundExecutor = Objects.requireNonNull(e); return this; }
        public Builder<T> withMinFrames(int minFrames) { if (minFrames <= 0) throw new IllegalArgumentException(); this.minFrames = minFrames; return this; }
        public Builder<T> withLoadTimeout(long loadTimeout) { if (loadTimeout <= 0) throw new IllegalArgumentException(); this.loadTimeout = loadTimeout; return this; }
        public Builder<T> withForwardWindowSize(int size) { if (size <= 0) throw new IllegalArgumentException(); this.forwardWindowSize = size; return this; }
        public Builder<T> withBackwardWindowSize(int size) { if (size <= 0) throw new IllegalArgumentException(); this.backwardWindowSize = size; return this; }
        public Builder<T> withMappingWindowSize(int size) { if (size <= 0) throw new IllegalArgumentException(); this.mappingWindowSize = size; return this; }
        public Builder<T> withFrameDataEvictionFactory(EvictionPolicyFactory<Integer> f) { this.frameDataEvictionFactory = f; return this; }
        public Builder<T> withDecodedFrameEvictionFactory(EvictionPolicyFactory<Integer> f) { this.decodedFrameEvictionFactory = f; return this; }
        public Builder<T> withRenderedFrameEvictionFactory(EvictionPolicyFactory<Integer> f) { this.renderedFrameEvictionFactory = f; return this; }
        public Builder<T> withPoolManager(PoolManager pm) { this.poolManager = pm; return this; }
        public Builder<T> withCacheManager(CacheManager cm) { this.cacheManager = Objects.requireNonNull(cm); return this; }
        public Builder<T> withRenderImageType(Class<T> type) { this.renderImageType = Objects.requireNonNull(type); return this; }
        public Builder<T> withSourceResolver(SourceResolver resolver) { this.sourceResolver = Objects.requireNonNull(resolver); return this; }

        public DefaultDecoder<T> build() {
            Objects.requireNonNull(rendererFactory, "渲染器工厂不能为 null");
            Objects.requireNonNull(handlerFactory, "处理器工厂不能为 null");
            Objects.requireNonNull(cacheManager, "缓存管理器不能为 null");
            if (poolManager != null) {
                Objects.requireNonNull(renderImageType, "启用对象池时必须提供 renderImageType");
            }

            DefaultDecoder<T> decoder = new DefaultDecoder<>(this);

            checkRequiredCache(decoder.cacheManager, CacheNames.FRAME_DATA_CACHE);
            checkRequiredCache(decoder.cacheManager, CacheNames.DECODE_FRAME_CACHE);
            checkRequiredCache(decoder.cacheManager, CacheNames.RENDER_FRAME_CACHE);

            return decoder;
        }

        private static void checkRequiredCache(CacheManager cm, String name) {
            if (!cm.contains(name)) {
                throw new IllegalArgumentException("缓存管理器缺少必需的缓存: " + name);
            }
        }
    }

    // ===========================================================================
    // 构造器
    // ===========================================================================

    private DefaultDecoder(Builder<T> builder) {
        this.lzwDecompressorFactory = builder.lzwDecompressorFactory;
        this.rendererFactory = builder.rendererFactory;
        this.handlerFactory = builder.handlerFactory;
        this.mappingManagerFactory = builder.mappingManagerFactory;
        this.sourceResolver = builder.sourceResolver;

        this.minFrames = builder.minFrames;
        this.loadTimeout = builder.loadTimeout;
        this.forwardWindowSize = builder.forwardWindowSize;
        this.backwardWindowSize = builder.backwardWindowSize;
        this.mappingWindowSize = builder.mappingWindowSize;

        if (builder.readExecutor != null) { this.readExecutor = builder.readExecutor; this.ownReadExecutor = false; }
        else { this.readExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "ReadThread")); this.ownReadExecutor = true; }
        if (builder.decodeExecutor != null) { this.decodeExecutor = builder.decodeExecutor; this.ownDecodeExecutor = false; }
        else { this.decodeExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "DecodeThread")); this.ownDecodeExecutor = true; }
        if (builder.renderExecutor != null) { this.renderExecutor = builder.renderExecutor; this.ownRenderExecutor = false; }
        else { this.renderExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "RenderThread")); this.ownRenderExecutor = true; }
        if (builder.wraparoundExecutor != null) { this.wraparoundExecutor = builder.wraparoundExecutor; this.ownWraparoundExecutor = false; }
        else { this.wraparoundExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "WraparoundThread")); this.ownWraparoundExecutor = true; }

        this.frameDataEvictionFactory = builder.frameDataEvictionFactory != null ? builder.frameDataEvictionFactory : new DefaultFrameDataEvictionPolicyFactory();
        this.decodedFrameEvictionFactory = builder.decodedFrameEvictionFactory != null ? builder.decodedFrameEvictionFactory : new DefaultDecodedFrameEvictionPolicyFactory();
        this.renderedFrameEvictionFactory = builder.renderedFrameEvictionFactory != null ? builder.renderedFrameEvictionFactory : new DefaultRenderedFrameEvictionPolicyFactory();

        this.poolManager = builder.poolManager;
        this.renderImageType = builder.renderImageType;
        this.cacheManager = builder.cacheManager;

        this.lzwDecompressor = lzwDecompressorFactory.create(poolManager);
        this.parser = (builder.parser != null) ? builder.parser
                : new DefaultParser.Builder().withLzwDecompressor(lzwDecompressor).withPoolManager(poolManager).build();

        // --- 创建固定的移除监听器 ---
        this.frameDataRemovalListener = event -> {
            FrameData fd = event.value;
            if (fd != null && poolManager != null) {
                PoolUtils.returnCompressedDataBuffer(poolManager, fd.compressedBuffer);
            }
        };
        this.decodeFrameRemovalListener = event -> {
            DecodeFrame df = event.value;
            if (df != null && poolManager != null) {
                PoolUtils.returnPixelIndices(poolManager, df.pixelIndices);
                if (df.isLocalColorTable) {
                    PoolUtils.returnColorTableInt(poolManager, df.colorTable);
                }
            }
        };
        this.renderFrameRemovalListener = event -> {
            T value = event.value;
            if (value != null && renderImagePool != null) {
                renderImagePool.release(value);
            }
        };

        // --- 一次性绑定监听器（缓存实例生命周期与解码器相同，clear() 不清除监听器）---
        Cache<Integer, FrameData> fdCache = getCache(CacheNames.FRAME_DATA_CACHE);
        fdCache.addRemovalListener(frameDataRemovalListener);
        Cache<Integer, DecodeFrame> dfCache = getCache(CacheNames.DECODE_FRAME_CACHE);
        dfCache.addRemovalListener(decodeFrameRemovalListener);
        Cache<Integer, T> rCache = getRequiredCache(CacheNames.RENDER_FRAME_CACHE);
        rCache.addRemovalListener(renderFrameRemovalListener);
    }

    // ===========================================================================
    // 公开 API
    // ===========================================================================

    @Override
    public synchronized void load(String source) {
        closeState.checkClosed();
        reset();
        try {
            SourceResolver.SourceResult result = sourceResolver.resolve(source);
            InputStream is = result.inputStream();
            try {
                submitParseTaskAndWait(is, result.cachePath());
            } catch (Throwable t) {
                closeQuietly(is);
                throw t;
            }
        } catch (IOException e) {
            throw new DecoderException("加载 GIF 源失败: " + source, e);
        }
    }

    @Override
    public synchronized void load(InputStream inputStream) {
        closeState.checkClosed();
        reset();
        try {
            submitParseTaskAndWait(inputStream, null);
        } catch (Throwable t) {
            closeQuietly(inputStream);
            throw t;
        }
    }

    @Override
    public synchronized int getFrameCount() {
        closeState.checkClosed();
        return frameInfos.size();
    }

    @Override
    public synchronized CompletableFuture<T> getFrame(int index) {
        closeState.checkClosed();
        if (header == null || frameInfos.isEmpty()) {
            throw new IllegalStateException("GIF 尚未加载，请先调用 load 方法");
        }
        cancelCurrentTask();
        int totalFrames = frameInfos.size();
        if (index < 0 || index >= totalFrames) {
            return CompletableFuture.failedFuture(new IndexOutOfBoundsException(
                    "帧索引 " + index + " 超出当前已知范围 [0, " + (totalFrames - 1) + "]"));
        }
        boolean isForwardDirection = DirectionHelper.isForwardDirection(lastRequested, index, totalFrames, lastDirection);
        lastRequested = index;
        lastDirection = isForwardDirection;

        CompletableFuture<T> resultFuture = new CompletableFuture<>();
        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try {
                Cache<Integer, T> renderCache = getRequiredCache(CacheNames.RENDER_FRAME_CACHE);
                T frame = renderCache.get(index);
                if (frame == null) {
                    renderCacheFramesInternal(index, isForwardDirection, totalFrames, resultFuture::complete);
                } else {
                    resultFuture.complete(frame);
                    renderCacheFramesInternal(index, isForwardDirection, totalFrames, null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                resultFuture.completeExceptionally(e);
            } catch (Exception e) {
                resultFuture.completeExceptionally(new DecoderException("获取第 " + index + " 帧失败", e));
            }
        }, renderExecutor);
        currentTaskFuture.set(task);
        task.whenComplete((r, e) -> currentTaskFuture.compareAndSet(task, null));
        return resultFuture;
    }

    @Override
    public synchronized int getDelayTime(int index) {
        closeState.checkClosed();
        return frameInfos.get(index).getDelayTime();
    }

    @Override
    public synchronized int getLoopCount() {
        closeState.checkClosed();
        return extensionContainer.getApplicationExtension().getLoopCount();
    }

    @Override
    public synchronized boolean isLoadComplete() {
        closeState.checkClosed();
        return loadComplete;
    }

    @Override
    public synchronized ApplicationExtension getApplicationExtension() {
        closeState.checkClosed();
        return extensionContainer.getApplicationExtension();
    }

    @Override
    public synchronized List<CommentExtension> getCommentExtensions() {
        closeState.checkClosed();
        return extensionContainer.getCommentExtensions();
    }

    @Override
    public synchronized List<PlainTextExtension> getPlainTextExtensions() {
        closeState.checkClosed();
        return extensionContainer.getPlainTextExtensions();
    }

    @Override
    public synchronized void reset() {
        closeState.checkClosed();
        cancelCurrentTask();
        clearCache(CacheNames.FRAME_DATA_CACHE);
        clearCache(CacheNames.DECODE_FRAME_CACHE);
        clearCache(CacheNames.RENDER_FRAME_CACHE);
        clearRenderImagePool();
        frameInfos.clear();
        extensionContainer.clear();
        header = null;
        lastRequested = -1;
        lastDirection = true;
        loadComplete = false;
        streamMode = false;
    }

    @Override
    public synchronized void close() throws Exception {
        closeState.checkClosed();
        closeState.markAsClosed();
        cancelCurrentTask();
        shutdownAllExecutors();
        closeQuietly(parser);
        closeQuietly(mappingManager);
        closeCacheManager();
        closeAndRemoveRenderImagePool();
    }

    // ===========================================================================
    // 内部辅助方法
    // ===========================================================================

    private void closeCacheManager() {
        if (cacheManager != null) {
            try { cacheManager.closeAllCaches(); } catch (IOException ignored) { }
            closeQuietly(cacheManager);
        }
    }

    private void clearCache(String name) {
        Cache<?, ?> cache = cacheManager.getCache(name);
        if (cache != null) cache.clear();
    }

    private void clearRenderImagePool() {
        if (renderImagePool != null) renderImagePool.clear();
    }

    private void closeAndRemoveRenderImagePool() {
        if (renderImagePool != null) {
            renderImagePool.clear();
            if (poolManager != null && renderImageType != null) {
                poolManager.removePool(renderImageType, PoolNames.RENDER_IMAGE);
            }
            renderImagePool = null;
        }
    }

    @SuppressWarnings("unchecked")
    private <K, V> Cache<K, V> getRequiredCache(String name) {
        Cache<?, ?> cache = cacheManager.getCache(name);
        if (cache == null) throw new IllegalStateException("必需的缓存 \"" + name + "\" 未注册。");
        return (Cache<K, V>) cache;
    }

    private <V> Cache<Integer, V> getCache(String name) {
        return cacheManager.<Integer, V>getCache(name);
    }

    private void initResources(Header header, String filePath) throws IOException {
        Objects.requireNonNull(header, "头部信息不能为 null");
        this.header = header;
        streamMode = (filePath == null);

        int width = header.getWidth();
        int height = header.getHeight();
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("GIF 宽高不合法");

        if (poolManager != null && renderImageType != null) {
            ObjectPool<T> pool = poolManager.getPool(renderImageType, PoolNames.RENDER_IMAGE);
            if (pool != null) {
                pool.clear();
                this.renderImagePool = pool;
            } else {
                this.renderImagePool = null;
            }
        } else {
            this.renderImagePool = null;
        }

        Renderer<T> renderer = rendererFactory.create(poolManager, width, height);

        if (filePath != null) {
            if (mappingManager != null) { mappingManager.close(); mappingManager = null; }
            mappingManager = mappingManagerFactory.create(filePath);
        }

        readHandler = handlerFactory.createReadHandler(mappingManager, getCache(CacheNames.FRAME_DATA_CACHE), poolManager);
        decodeHandler = handlerFactory.createDecodeHandler(lzwDecompressor, readHandler, getCache(CacheNames.DECODE_FRAME_CACHE));
        renderHandler = handlerFactory.createRenderHandler(renderer, decodeHandler, getRequiredCache(CacheNames.RENDER_FRAME_CACHE));
    }

    // ===========================================================================
    // 解析与流水线
    // ===========================================================================

    private void submitParseTaskAndWait(InputStream in, String filePath) {
        CompletableFuture<Void> loadFuture = new CompletableFuture<>();
        parser.parseAsync(in, new ParseCallback() {
            @Override
            public void onParseComplete() {
                loadComplete = true;
                loadFuture.complete(null);
                closeQuietly(in);
                System.out.println("GIF 解析完成，总帧数：" + frameInfos.size());
            }

            @Override
            public void onHeader(Header header) {
                try {
                    initResources(header, filePath);
                } catch (IOException e) {
                    loadFuture.completeExceptionally(new DecoderException("初始化 GIF 资源失败", e));
                }
            }

            @Override
            public void onGlobalExtensionParsed(Extension extension) {
                extension.applyTo(extensionContainer);
            }

            @Override
            public void onFrameParsed(FrameInfo frameInfo) {
                frameInfos.add(frameInfo);
                if (frameInfos.size() >= minFrames) {
                    loadFuture.complete(null);
                }
            }

            @Override
            public void onParseError(Exception error, boolean recovered) {
                closeQuietly(in);
                loadFuture.completeExceptionally(error);
            }
        });

        try {
            loadFuture.get(loadTimeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            loadFuture.cancel(true);
            throw new DecoderException("加载被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DecoderException) throw (DecoderException) cause;
            throw new DecoderException("解析过程中发生异常", cause);
        } catch (TimeoutException e) {
            loadFuture.cancel(true);
            throw new DecoderException("GIF 解析超时 (" + loadTimeout + " 秒)，当前已解析帧数：" + frameInfos.size());
        }
    }

    private void renderCacheFramesInternal(int currentStartIndex, boolean isForwardDirection, int totalFrames,
                                           Consumer<T> onCurrentFrame) throws InterruptedException {
        Thread mainThread = Thread.currentThread();
        Cache<Integer, T> renderCache = getRequiredCache(CacheNames.RENDER_FRAME_CACHE);

        CacheWindowHelper.CacheRange range = CacheWindowHelper.computeRenderCacheRange(
                currentStartIndex, isForwardDirection, totalFrames,
                frameInfos, renderCache, forwardWindowSize, backwardWindowSize);
        CacheWindowHelper.PartialRange primaryRange = range.primaryRange;
        CacheWindowHelper.PartialRange wraparoundRange = range.wraparoundRange;

        long totalContinuousDataSize = calculateRequiredDataSize(range, totalFrames);
        int finalMappingWindowSize = (int) Math.max(totalContinuousDataSize, mappingWindowSize);

        long baseOffset;
        if (range.isForwardDirection) {
            FrameInfo firstFrame = frameInfos.get(primaryRange.startIndex);
            baseOffset = firstFrame.getFrameStartOffset();
        } else {
            int lastFrameIndex = primaryRange.startIndex + primaryRange.frameCount - 1;
            FrameInfo lastFrame = frameInfos.get(lastFrameIndex);
            baseOffset = lastFrame.getFrameEndOffset();
        }
        long availableFileSize = frameInfos.get(totalFrames - 1).getFrameEndOffset() + 1;

        final MappingContext sharedMappingContext = streamMode ? null : new MappingContext.Builder()
                                                                        .withMappingDirection(range.isForwardDirection ? MappingDirection.FORWARD : MappingDirection.BACKWARD)
                                                                        .withWindowBaseOffset(baseOffset)
                                                                        .withWindowSize(finalMappingWindowSize)
                                                                        .withAvailableFileSize(availableFileSize)
                                                                        .build();

        processPath(primaryRange, true, range, currentStartIndex, totalFrames, onCurrentFrame, mainThread, sharedMappingContext);

        CompletableFuture<Void> wraparoundFuture = CompletableFuture.completedFuture(null);
        if (!wraparoundRange.isEmpty()) {
            wraparoundFuture = CompletableFuture.runAsync(() -> {
                try {
                    processPath(wraparoundRange, false, range, currentStartIndex, totalFrames, onCurrentFrame, mainThread, sharedMappingContext);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }, wraparoundExecutor);
        }

        try {
            wraparoundFuture.get();
        } catch (InterruptedException e) {
            wraparoundFuture.cancel(true);
            mainThread.interrupt();
            throw new InterruptedException("渲染线程被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InterruptedException) {
                wraparoundFuture.cancel(true);
                mainThread.interrupt();
                throw new InterruptedException("渲染线程被中断");
            }
            throw new RuntimeException("回绕路径渲染异常", cause);
        }
    }

    private void processPath(CacheWindowHelper.PartialRange range, boolean isPrimary,
                             CacheWindowHelper.CacheRange fullRange, int startIndex, int totalFrames,
                             Consumer<T> onCurrentFrame, Thread mainThread, MappingContext mappingContext) throws InterruptedException {
        int frameCount = range.frameCount;
        boolean isForwardDirection = fullRange.isForwardDirection;
        Cache<Integer, T> renderCache = getRequiredCache(CacheNames.RENDER_FRAME_CACHE);

        CompletableFuture<?>[] preFutures = new CompletableFuture[2];

        try {
            for (int i = 0; i < frameCount; i++) {
                if (mainThread.isInterrupted() || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("渲染线程被中断");
                }

                int frameIndex = range.startIndex + i;
                int count = 0;

                if (isForwardDirection || i < frameCount - 2) {
                    int readIndex = (frameIndex + 2) % totalFrames;
                    if (renderCache.get(readIndex) == null) {
                        HandlerContext readContext = buildReadContext(readIndex, mappingContext);
                        preFutures[count++] = CompletableFuture.runAsync(() -> readHandler.process(readContext), readExecutor);
                    }
                }

                if (isForwardDirection || i < frameCount - 1) {
                    int decodeIndex = (frameIndex + 1) % totalFrames;
                    if (renderCache.get(decodeIndex) == null) {
                        HandlerContext decodeContext = buildDecodeContext(decodeIndex, mappingContext);
                        preFutures[count++] = CompletableFuture.runAsync(() -> decodeHandler.process(decodeContext), decodeExecutor);
                    }
                }

                HandlerContext renderContext = buildRenderContext(frameIndex, mappingContext);
                T result = renderHandler.process(renderContext);

                if (frameIndex == startIndex && onCurrentFrame != null) {
                    onCurrentFrame.accept(result);
                }

                for (int j = 0; j < count; j++) {
                    preFutures[j].join();
                }

                evictCachesAfterRender(frameIndex, isPrimary, fullRange, startIndex, totalFrames);
            }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("帧渲染循环执行失败：" + e.getMessage(), e);
        }
    }

    private void evictCachesAfterRender(int currentFrameIndex, boolean isPrimary,
                                        CacheWindowHelper.CacheRange fullRange, int requestStartIndex, int totalFrames) {
        boolean needPreviousFrame = isRestoreMode(currentFrameIndex);
        EvictionPolicyContext context = new EvictionPolicyContext(
                currentFrameIndex, isPrimary, fullRange, requestStartIndex,
                forwardWindowSize, backwardWindowSize, totalFrames, needPreviousFrame);
        Cache<Integer, FrameData> fdCache = getCache(CacheNames.FRAME_DATA_CACHE);
        if (fdCache != null) fdCache.evictEntries(frameDataEvictionFactory.create(context));
        Cache<Integer, DecodeFrame> dfCache = getCache(CacheNames.DECODE_FRAME_CACHE);
        if (dfCache != null) dfCache.evictEntries(decodedFrameEvictionFactory.create(context));
        Cache<Integer, T> renderCache = getRequiredCache(CacheNames.RENDER_FRAME_CACHE);
        if (renderCache != null) renderCache.evictEntries(renderedFrameEvictionFactory.create(context));
    }

    private boolean isRestoreMode(int frameIndex) {
        return frameInfos.get(frameIndex).getDisplayMode() == DisplayMode.RESTORE_PREVIOUS;
    }

    // ===========================================================================
    // 上下文构建
    // ===========================================================================

    private HandlerContext buildReadContext(int frameIndex, MappingContext mappingContext) {
        return buildBaseContext(frameIndex, mappingContext).build();
    }

    private HandlerContext buildDecodeContext(int frameIndex, MappingContext mappingContext) {
        return buildBaseContext(frameIndex, mappingContext).build();
    }

    private HandlerContext buildRenderContext(int frameIndex, MappingContext mappingRequest) {
        FrameInfo current = frameInfos.get(frameIndex);
        FrameInfo prev = null;
        int refIndex = -1;
        if (current.getKeyframeIndex() != frameIndex) {
            prev = frameInfos.get(frameIndex - 1);
            refIndex = prev.getDisplayMode() == DisplayMode.RESTORE_PREVIOUS ? frameIndex - 2 : frameIndex - 1;
        }
        return buildBaseContext(frameIndex, mappingRequest)
                .withPrevFrameInfo(prev)
                .withReferenceFrameIndex(refIndex)
                .withBackgroundColorIndex(header.getBackgroundColorIndex())
                .build();
    }

    private HandlerContext.Builder buildBaseContext(int frameIndex, MappingContext mappingContext) {
        HandlerContext.Builder builder = new HandlerContext.Builder()
                .withCurrentFrameIndex(frameIndex)
                .withCurrentFrameInfo(frameInfos.get(frameIndex))
                .withMappingContext(mappingContext);
        int[] global = header.getGlobalColorTable();
        if (global != null) builder.withGlobalColorTable(global);
        return builder;
    }

    // ===========================================================================
    // 任务取消与线程池管理
    // ===========================================================================

    private void cancelCurrentTask() {
        Future<?> active = currentTaskFuture.getAndSet(null);
        if (active != null && !active.isDone()) {
            active.cancel(true);
            try {
                active.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | CancellationException | TimeoutException | ExecutionException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void shutdownAllExecutors() {
        shutdownExecutor(readExecutor, ownReadExecutor);
        shutdownExecutor(decodeExecutor, ownDecodeExecutor);
        shutdownExecutor(renderExecutor, ownRenderExecutor);
        shutdownExecutor(wraparoundExecutor, ownWraparoundExecutor);
    }

    private void shutdownExecutor(ExecutorService executor, boolean owned) {
        if (!owned || executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignored) { }
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (IOException ignored) { }
        }
    }

    // ===========================================================================
    // 数据范围计算
    // ===========================================================================

    private long calculateRequiredDataSize(CacheWindowHelper.CacheRange range, int totalFrames) {
        if (totalFrames == 0) return 0;
        CacheWindowHelper.PartialRange primary = range.primaryRange;
        CacheWindowHelper.PartialRange wraparound = range.wraparoundRange;
        return range.isForwardDirection ? calculateForwardSize(primary, wraparound, totalFrames)
                : calculateBackwardSize(primary, wraparound, totalFrames);
    }

    private long calculateForwardSize(CacheWindowHelper.PartialRange primary, CacheWindowHelper.PartialRange wraparound, int totalFrames) {
        if (!primary.isEmpty() && wraparound.isEmpty()) {
            int end = primary.getEndIndex() + 2;
            if (end < totalFrames) return getRangeSize(primary.startIndex, end);
            int wrapEnd = end - totalFrames;
            if (wrapEnd + 1 >= primary.startIndex) return getRangeSize(0, totalFrames - 1);
            return getRangeSize(primary.startIndex, totalFrames - 1) + getRangeSize(0, wrapEnd);
        }
        long primarySize = primary.isEmpty() ? 0 : getRangeSize(primary.startIndex, primary.getEndIndex());
        if (!wraparound.isEmpty()) {
            int wrapEnd = wraparound.getEndIndex() + 2;
            if (wrapEnd + 1 >= primary.startIndex) return getRangeSize(0, totalFrames - 1);
            return primarySize + getRangeSize(0, wrapEnd);
        }
        return primarySize;
    }

    private long calculateBackwardSize(CacheWindowHelper.PartialRange primary, CacheWindowHelper.PartialRange wraparound, int totalFrames) {
        long primarySize = primary.isEmpty() ? 0 : getRangeSize(primary.startIndex, primary.getEndIndex());
        long wrapSize = wraparound.isEmpty() ? 0 : getRangeSize(wraparound.startIndex, wraparound.getEndIndex());
        return primarySize + wrapSize;
    }

    private long getRangeSize(int startIndex, int endIndex) {
        FrameInfo first = frameInfos.get(startIndex);
        FrameInfo last = frameInfos.get(endIndex);
        return startIndex == 0 ? last.getFrameEndOffset() + 1
                : last.getFrameEndOffset() - first.getFrameStartOffset() + 1;
    }
}