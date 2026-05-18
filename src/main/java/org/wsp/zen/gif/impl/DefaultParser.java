package org.wsp.zen.gif.impl;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.wsp.zen.gif.core.ExtensionCallback;
import org.wsp.zen.gif.core.LzwDecompressor;
import org.wsp.zen.gif.core.ParseCallback;
import org.wsp.zen.gif.core.Parser;
import org.wsp.zen.gif.core.RecoveryCallback;
import org.wsp.zen.gif.exception.ParseException;
import org.wsp.zen.gif.exception.ParseRecoveryException;
import org.wsp.zen.gif.exception.LzwCorruptedDataException;
import org.wsp.zen.gif.extension.ApplicationExtension;
import org.wsp.zen.gif.extension.CommentExtension;
import org.wsp.zen.gif.extension.Extension;
import org.wsp.zen.gif.extension.GraphicsControlExtension;
import org.wsp.zen.gif.extension.PlainTextExtension;
import org.wsp.zen.gif.model.ChunkedData;
import org.wsp.zen.gif.model.DisplayMode;
import org.wsp.zen.gif.model.FrameInfo;
import org.wsp.zen.gif.model.Header;
import org.wsp.zen.gif.model.ImageDescriptor;
import org.wsp.zen.gif.util.CloseState;
import org.wsp.zen.gif.util.PoolUtils;
import org.wsp.zen.io.core.ReadPositionAware;
import org.wsp.zen.io.util.StreamUtils;
import org.wsp.zen.pool.core.PoolManager;

/**
 * GIF 解析器的默认实现，支持异步解析、错误恢复、中断取消等特性。
 * <p>
 * 该类从输入流中读取 GIF 数据，解析文件头、扩展块、图像描述符，并通过回调通知调用方。
 * 解析过程在独立的线程池中异步执行，可通过 {@link #parseAsync(InputStream, ParseCallback)}
 * 提交任务，并返回 {@link CompletableFuture} 用于等待完成或取消。
 * </p>
 *
 * <p><b>主要特性：</b>
 * <ul>
 *   <li>符合 GIF 87a/89a 规范，解析文件头、全局/局部颜色表、扩展块、图像数据等。</li>
 *   <li>支持 LZW 解码（通过 {@link LzwDecompressor} 委托）。</li>
 *   <li>错误恢复机制：遇到损坏数据时可尝试跳过并继续解析。</li>
 *   <li>支持关键帧检测，用于后续渲染优化。</li>
 *   <li>线程安全，支持取消当前解析任务。</li>
 * </ul>
 * </p>
 *
 * <p><b>依赖与降级：</b>
 * LZW 解压器必须通过 Builder 提供。对象池管理器（{@link PoolManager}）为可选项：
 * <ul>
 *   <li>若提供了对象池管理器，解析器会利用其中的压缩数据池、颜色表字节池等临时数组池，
 *       以减少内存分配和 GC 压力，并在使用完毕后通过 {@link PoolUtils} 及时归还。</li>
 *   <li>若未提供（{@code null}），解析器将直接分配临时数组，功能完全不受影响。</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 解析器实例自身是线程安全的，但建议同一时间只处理一个解析任务。
 * 内部使用 {@link AtomicReference} 管理当前任务，并通过 {@link CloseState} 控制生命周期。
 * </p>
 *
 * @author wsp
 * @version 1.1
 * @see Parser
 * @see ParseCallback
 */
public class DefaultParser implements Parser {

    // ==================== 核心组件 ====================

    private final LzwDecompressor decompressor;
    private final PoolManager poolManager;          // 可选，允许为 null
    private final int maxRecoveryAttempts;
    private final int maxConsecutiveZeros;

    // ==================== 线程池 ====================

    private final ExecutorService parseExecutor;
    private final boolean ownParseExecutor;

    // ==================== 任务管理 ====================

    private final AtomicReference<Future<?>> currentTaskFuture = new AtomicReference<>();

    // ==================== 状态控制 ====================

    private final CloseState closeState = new CloseState("解析器");

    // ===========================================================================
    // 构造器与 Builder
    // ===========================================================================

    /**
     * 私有构造器，通过 Builder 构建实例。
     */
    private DefaultParser(Builder builder) {
        this.decompressor = builder.lzwDecompressor;
        this.poolManager = builder.poolManager;   // 允许为 null
        this.maxRecoveryAttempts = builder.maxRecoveryAttempts;
        this.maxConsecutiveZeros = builder.maxConsecutiveZeros;

        if (builder.parseExecutor != null) {
            this.parseExecutor = builder.parseExecutor;
            this.ownParseExecutor = false;
        } else {
            this.parseExecutor = Executors.newSingleThreadExecutor(
                    r -> new Thread(r, "GifParseThread"));
            this.ownParseExecutor = true;
        }
    }

    /**
     * Builder 模式，用于构造 {@link DefaultParser} 实例。
     */
    public static class Builder {
        private int maxRecoveryAttempts = 5;
        private int maxConsecutiveZeros = 5000;
        private ExecutorService parseExecutor;

        private LzwDecompressor lzwDecompressor;
        private PoolManager poolManager;   // 可选

        public DefaultParser build() {
            Objects.requireNonNull(lzwDecompressor, "LZW 解压器不能为 null");
            return new DefaultParser(this);
        }

        public Builder withLzwDecompressor(LzwDecompressor lzwDecompressor) {
            this.lzwDecompressor = Objects.requireNonNull(lzwDecompressor);
            return this;
        }

        public Builder withPoolManager(PoolManager poolManager) {
            this.poolManager = poolManager;
            return this;
        }

        public Builder withMaxRecoveryAttempts(int maxRecoveryAttempts) {
            this.maxRecoveryAttempts = maxRecoveryAttempts;
            return this;
        }

        public Builder withMaxConsecutiveZeros(int maxConsecutiveZeros) {
            this.maxConsecutiveZeros = maxConsecutiveZeros;
            return this;
        }

        public Builder withParseExecutor(ExecutorService parseExecutor) {
            this.parseExecutor = Objects.requireNonNull(parseExecutor, "解析线程池不能为 null");
            return this;
        }
    }

    // ===========================================================================
    // 内部状态类
    // ===========================================================================

    /**
     * 解析器状态，跟踪帧计数、关键帧索引和当前图形控制扩展。
     */
    private static class ParserState {
        public final int canvasWidth;
        public final int canvasHeight;

        private int frameCount = 0;
        private int currentKeyframeIndex = 0;
        private FrameInfo previousFrameInfo;
        private GraphicsControlExtension currentGraphicsControl;

        public ParserState(Header header) {
            Objects.requireNonNull(header, "格式头对象不能为 null");
            this.canvasWidth = header.getWidth();
            this.canvasHeight = header.getHeight();
        }

        public int getFrameCount() { return frameCount; }
        public int getCurrentKeyframeIndex() { return currentKeyframeIndex; }

        public int advanceFrame(FrameInfo currentFrame, boolean isKeyframe) {
            previousFrameInfo = Objects.requireNonNull(currentFrame, "当前帧元数据对象不能为 null");
            if (isKeyframe) currentKeyframeIndex = frameCount;
            return frameCount++;
        }

        public FrameInfo getPreviousFrameInfo() { return previousFrameInfo; }
        public void setGraphicsControlExtension(GraphicsControlExtension gce) { this.currentGraphicsControl = gce; }
        public GraphicsControlExtension getGraphicsControlExtension() { return currentGraphicsControl; }
    }

    // ===========================================================================
    // 公开 API
    // ===========================================================================

    @Override
    public synchronized CompletableFuture<Void> parseAsync(InputStream in, ParseCallback callback) {
        closeState.checkClosed();
        Objects.requireNonNull(in, "输入流不能为 null");
        Objects.requireNonNull(callback, "解析回调不能为 null");

        cancelCurrentTask();

        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try {
                doParse(in, callback);
            } catch (InterruptedException e) {
                Thread.interrupted();
                callback.onParseInterrupted();
            } catch (Exception e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
                callback.onParseError(e, e instanceof ParseRecoveryException);
            }
        }, parseExecutor);

        currentTaskFuture.set(task);
        task.whenComplete((result, error) -> currentTaskFuture.compareAndSet(task, null));
        return task;
    }

    // ===========================================================================
    // 核心解析逻辑
    // ===========================================================================

    private void doParse(InputStream in, ParseCallback callback)
            throws IOException, LzwCorruptedDataException, InterruptedException {
        Header header = parseHeader(in);
        callback.onHeader(header);
        ParserState parserState = new ParserState(header);

        int blockCode = -1;
        while ((blockCode = StreamUtils.readByte(in)) != 0x3b && blockCode != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("解析线程被中断");

            try {
                if (blockCode != 0x21 && blockCode != 0x2c)
                    throw new ParseException("非法块标识符: 0x" + Integer.toHexString(blockCode));

                switch (blockCode) {
                    case 0x21:
                        blockCode = StreamUtils.readByte(in);
                        if (blockCode == -1) throw new EOFException("处理扩展块时流意外结束");
                        if (blockCode == 0x00) break;
                        parseExtensionBlock(in, blockCode, new ExtensionCallback() {
                            @Override public void onGraphicsControlExtension(GraphicsControlExtension gce) {
                                parserState.setGraphicsControlExtension(gce);
                            }
                            @Override public void onGlobalExtension(Extension ext) {
                                callback.onGlobalExtensionParsed(ext);
                            }
                        });
                        break;

                    case 0x2c:
                        ImageDescriptor descriptor = parseImageDescriptor(in, parserState);
                        handleFrame(parserState, descriptor, callback);
                        break;
                }
            } catch (ParseException | LzwCorruptedDataException e) {
                if (maxRecoveryAttempts <= 0) throw new ParseException("数据解析异常（不允许重试）", e);
                System.err.println("数据解析异常：" + e.getMessage() + "，系统正在尝试获取下一个数据块...");
                recoverFromParsingError(in, parserState, new RecoveryCallback() {
                    @Override public void onGlobalExtensionRecovered(Extension ext) {
                        callback.onGlobalExtensionParsed(ext);
                    }
                    @Override public void onImageDescriptorRecovered(ImageDescriptor desc) {
                        handleFrame(parserState, desc, callback);
                    }
                    @Override public void onGraphicsControlExtensionRecovered(GraphicsControlExtension gce) {
                        parserState.setGraphicsControlExtension(gce);
                    }
                });
            } catch (IOException e) {
                throw new ParseException("不可恢复的 IO 异常", e);
            } catch (Exception e) {
                throw new ParseException("未知解析错误: " + e.getMessage(), e);
            }
        }
        callback.onParseComplete();
    }

    private static void handleFrame(ParserState state, ImageDescriptor descriptor, ParseCallback callback) {
        boolean isKeyframe = state.getFrameCount() == descriptor.getKeyframeIndex();
        FrameInfo frameInfo = new FrameInfo(state.getGraphicsControlExtension(), descriptor);
        callback.onFrameParsed(frameInfo);
        state.advanceFrame(frameInfo, isKeyframe);
        state.setGraphicsControlExtension(null);
    }

    // ===========================================================================
    // 错误恢复逻辑
    // ===========================================================================

    private void recoverFromParsingError(InputStream in, ParserState parserState, RecoveryCallback callback)
            throws IOException, InterruptedException {
        int recoveryAttempts = 0;
        int consecutiveZeroBytes = 0;
        int blockCode = -1;

        while (blockCode == 0x00 || (blockCode = StreamUtils.readByte(in)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("解析线程被中断");

            try {
                if (blockCode == 0x00) {
                    if (++consecutiveZeroBytes >= maxConsecutiveZeros)
                        throw new ParseRecoveryException("连续的 0x00 锚点超过阈值，连续：" + consecutiveZeroBytes + " 次");

                    blockCode = StreamUtils.readByte(in);
                    if (blockCode == -1) { callback.onNoValidDataRecovered(); return; }

                    switch (blockCode) {
                        case 0x21:
                            consecutiveZeroBytes = 0;
                            blockCode = StreamUtils.readByte(in);
                            if (blockCode == -1) { callback.onNoValidDataRecovered(); return; }
                            if (blockCode == 0x00) break;
                            parseExtensionBlock(in, blockCode, new ExtensionCallback() {
                                @Override public void onGraphicsControlExtension(GraphicsControlExtension gce) {
                                    callback.onGraphicsControlExtensionRecovered(gce);
                                }
                                @Override public void onGlobalExtension(Extension ext) {
                                    callback.onGlobalExtensionRecovered(ext);
                                }
                            });
                            return;

                        case 0x2c:
                            consecutiveZeroBytes = 0;
                            ImageDescriptor descriptor = parseImageDescriptor(in, parserState);
                            callback.onImageDescriptorRecovered(descriptor);
                            return;
                    }
                }
            } catch (ParseException | LzwCorruptedDataException e) {
                blockCode = -1;
                System.err.println("系统在尝试恢复过程中出现了错误: " + e.getMessage());
                if (++recoveryAttempts >= maxRecoveryAttempts)
                    throw new ParseRecoveryException("GIF 解析恢复尝试失败，已尝试：" + recoveryAttempts + " 次");
                System.out.println("系统正在尝试再次从错误数据中进行恢复，当前已尝试恢复次数：" +
                        recoveryAttempts + "，剩余可尝试恢复次数：" + (maxRecoveryAttempts - recoveryAttempts));
            } catch (Exception e) {
                throw new ParseRecoveryException("系统在解析 GIF 数据块时发生了致命错误（块类型：0x" +
                        Integer.toHexString(blockCode) + "）", e);
            }
        }
        callback.onNoValidDataRecovered();
    }

    // ===========================================================================
    // 扩展块解析
    // ===========================================================================

    private void parseExtensionBlock(InputStream in, int extType, ExtensionCallback callback) throws IOException {
        switch (extType) {
            case 0xff: callback.onGlobalExtension(parseApplicationExtension(in)); break;
            case 0xfe: callback.onGlobalExtension(parseCommentExtension(in)); break;
            case 0xf9: callback.onGraphicsControlExtension(parseGraphicsControlExtension(in)); break;
            case 0x01: callback.onGlobalExtension(parsePlainTextExtension(in)); break;
            default: StreamUtils.skipUnknownExtension(in); callback.onUnknownExtension(extType);
        }
    }

    // ==========================================================具体块解析===========================================
    // #############################################################################################################
    private Header parseHeader(InputStream in) throws IOException {
        String magicNumber = StreamUtils.readString(in, 3);
        if (magicNumber == null || !magicNumber.equals("GIF")) throw new ParseException("魔数必须为 GIF");

        String version = StreamUtils.readString(in, 3);
        if (version == null || (!version.equalsIgnoreCase("87a") && !version.equalsIgnoreCase("89a")))
            System.err.println("警告：未知的 GIF 版本 '" + version + "'，尝试继续解析");

        short width = (short) StreamUtils.readDoubleBytes(in);
        short height = (short) StreamUtils.readDoubleBytes(in);
        byte packedField = (byte) StreamUtils.readByte(in);

        byte backgroundColorIndex = (byte) StreamUtils.readByte(in);
        byte pixelAspectRatio = (byte) StreamUtils.readByte(in);

        int[] globalColorTable = null;
        if ((packedField & 0x80) != 0) {
            int colorTableSize = 1 << ((packedField & 0x07) + 1);
            globalColorTable = StreamUtils.readColorTable(in, colorTableSize, poolManager);
        }

        return new Header.Builder()
                .withMagicNumber(magicNumber)
                .withVersion(version)
                .withCanvasSize(width, height)
                .withPackedField(packedField)
                .withBackgroundColorIndex(backgroundColorIndex)
                .withPixelAspectRatio(pixelAspectRatio)
                .withGlobalColorTable(globalColorTable)
                .build();
    }

    // 扩展块使用 readSubBlocks，直接拿到精确长度的纯数据，不涉及对象池归还
    private ApplicationExtension parseApplicationExtension(InputStream in) throws IOException {
        int blockSize = StreamUtils.readByte(in);
        if (blockSize != 11) throw new ParseException("应用程序信息必须为 11 字节");
        String appInfo = StreamUtils.readString(in, blockSize);
        byte[] chunkData = StreamUtils.readSubBlocks(in);
        return new ApplicationExtension(appInfo, chunkData);
    }

    private CommentExtension parseCommentExtension(InputStream in) throws IOException {
        byte[] comments = StreamUtils.readSubBlocks(in);
        return new CommentExtension(comments);
    }

    private PlainTextExtension parsePlainTextExtension(InputStream in) throws IOException {
        int blockSize = StreamUtils.readByte(in);
        if (blockSize != 12) throw new ParseException("块大小必须为 12");
        short textGridPositionX = (short) StreamUtils.readDoubleBytes(in);
        short textGridPositionY = (short) StreamUtils.readDoubleBytes(in);
        short textGridWidth = (short) StreamUtils.readDoubleBytes(in);
        short textGridHeight = (short) StreamUtils.readDoubleBytes(in);
        byte charCellWidth = (byte) StreamUtils.readByte(in);
        byte charCellHeight = (byte) StreamUtils.readByte(in);
        byte textForegroundColorIndex = (byte) StreamUtils.readByte(in);
        byte textBackgroundColorIndex = (byte) StreamUtils.readByte(in);
        byte[] plainText = StreamUtils.readSubBlocks(in);
        return new PlainTextExtension.Builder()
                .withTextGridPosition(textGridPositionX, textGridPositionY)
                .withTextGridSize(textGridWidth, textGridHeight)
                .withCharCellSize(charCellWidth, charCellHeight)
                .withTextForegroundColorIndex(textForegroundColorIndex)
                .withTextBackgroundColorIndex(textBackgroundColorIndex)
                .withPlainText(plainText)
                .build();
    }

    private GraphicsControlExtension parseGraphicsControlExtension(InputStream in) throws IOException {
        int blockSize = StreamUtils.readByte(in);
        if (blockSize != 4) throw new ParseException("图形控制扩展的块大小必须为 4");
        byte packedField = (byte) StreamUtils.readByte(in);
        short delayTime = (short) StreamUtils.readDoubleBytes(in);
        byte transparentColorIndex = (byte) StreamUtils.readByte(in);
        int terminator = StreamUtils.readByte(in);
        if (terminator != 0x00) throw new ParseException("图形控制扩展的块终止符必须为 0x00");
        return new GraphicsControlExtension(packedField, delayTime, transparentColorIndex);
    }

    /**
     * 解析图像描述符块（0x2c）。
     * <p>
     * 根据数据源类型（文件映射或流式）采取不同的资源管理策略：
     * <ul>
     *   <li><b>文件映射模式</b>（in instanceof ReadPositionAware）：</li>
     *       局部颜色表和压缩数据仅在关键帧检测期间临时使用，检测完成后通过 {@link PoolUtils} 立即归还给对象池。
     *       ImageDescriptor 中不保存这些数据（后续通过文件映射读取）。
     *   <li><b>流模式</b>：</li>
     *       颜色表和压缩数据直接存入 ImageDescriptor，由后续流程（如缓存监听器）负责释放，
     *       此处不归还。
     * </ul>
     * </p>
     */
    private ImageDescriptor parseImageDescriptor(InputStream in, ParserState parserState)
            throws IOException, LzwCorruptedDataException {
        short offsetX = (short) StreamUtils.readDoubleBytes(in);
        short offsetY = (short) StreamUtils.readDoubleBytes(in);
        short width = (short) StreamUtils.readDoubleBytes(in);
        short height = (short) StreamUtils.readDoubleBytes(in);
        byte packedField = (byte) StreamUtils.readByte(in);

        // ---------- 图像描述符合法性校验 ----------
        if ((packedField & 0x18) != 0) {
            throw new ParseException("图像描述符保留位必须为 0，实际：" +
                    Integer.toBinaryString(packedField & 0xFF));
        }

        boolean isLocalColorTable = (packedField & 0x80) != 0;
        int[] pooledColorTable = null;   // 从池中申请的颜色表
        byte[] pooledCompressedBuffer = null; // 从池中申请的压缩数据

        try {
            if (isLocalColorTable) {
                int size = 1 << ((packedField & 0x7) + 1);
                pooledColorTable = StreamUtils.readColorTable(in, size, poolManager);
                if (pooledColorTable != null && pooledColorTable.length < size) {
                    throw new ParseException("局部颜色表数组长度小于所需：" +
                            pooledColorTable.length + " < " + size);
                }
            }

            byte minCodeSize = (byte) StreamUtils.readByte(in);
            int codeSize = minCodeSize & 0xFF;
            if (codeSize < 1 || codeSize > 8) {
                throw new ParseException("LZW 最小码长必须在 1~8 之间，实际：" + codeSize);
            }
            // --------------------------------

            long dataOffset = in instanceof ReadPositionAware ?
                    ((ReadPositionAware) in).getPosition() : -1;

            ChunkedData compressedChunk = StreamUtils.readChunkedData(in, poolManager);
            pooledCompressedBuffer = compressedChunk.compressedBuffer;

            // 关键帧检测（需要压缩数据）
            KeyframeContext keyframeContext = new KeyframeContext.Builder()
                    .withParserState(parserState)
                    .withInterlaced((packedField & 0x40) != 0)
                    .withOffset(offsetX, offsetY)
                    .withSize(width, height)
                    .withMinCodeSize(minCodeSize)
                    .withCompressedBuffer(pooledCompressedBuffer)
                    .build();

            boolean isKeyframe = determineIfKeyframe(keyframeContext);
            int keyframeIndex = isKeyframe ? parserState.getFrameCount() : parserState.getCurrentKeyframeIndex();

            // 根据数据源类型，决定 ImageDescriptor 中实际持有的数据
            final int[] descriptorColorTable;
            final byte[] descriptorCompressedBuffer;
            if (in instanceof ReadPositionAware) {
                // 文件映射模式：归还资源，ImageDescriptor 不持有
                descriptorColorTable = null;
                descriptorCompressedBuffer = null;
            } else {
                // 流模式：资源移交给 ImageDescriptor，由后续流程释放
                descriptorColorTable = pooledColorTable;
                descriptorCompressedBuffer = pooledCompressedBuffer;
            }

            return new ImageDescriptor.Builder()
                    .withOffset(offsetX, offsetY)
                    .withSize(width, height)
                    .withPackedField(packedField)
                    .withMinCodeSize(minCodeSize)
                    .withDataInfo(dataOffset, compressedChunk.subBlockTotalBytes)
                    .withLocalColorTable(descriptorColorTable)
                    .withCompressedBuffer(descriptorCompressedBuffer)
                    .withKeyframeIndex(keyframeIndex)
                    .build();
        } finally {
            // 文件映射模式：安全归还所有池化资源
            if (in instanceof ReadPositionAware) {
                if (pooledColorTable != null) {
                    PoolUtils.returnColorTableInt(poolManager, pooledColorTable);
                }
                if (pooledCompressedBuffer != null) {
                    PoolUtils.returnCompressedDataBuffer(poolManager, pooledCompressedBuffer);
                }
            }
        }
    }

    // ===========================================================================
    // 关键帧检测
    // ===========================================================================

    /**
     * 关键帧检测的上下文数据。
     */
    private static class KeyframeContext {
        public final FrameInfo prevFrameInfo;
        public final GraphicsControlExtension graphicsControl;
        public final int canvasWidth, canvasHeight;
        public final boolean isFirstFrame, isInterlaced;
        public final short offsetX, offsetY, width, height;
        public final byte minCodeSize;
        public final byte[] compressedBuffer;

        KeyframeContext(FrameInfo prev, GraphicsControlExtension gce, int cw, int ch,
                        boolean first, boolean interl, short ox, short oy, short w, short h,
                        byte mcs, byte[] compressedBuffer) {
            this.prevFrameInfo = prev; this.graphicsControl = gce;
            this.canvasWidth = cw; this.canvasHeight = ch;
            this.isFirstFrame = first; this.isInterlaced = interl;
            this.offsetX = ox; this.offsetY = oy; this.width = w; this.height = h;
            this.minCodeSize = mcs; this.compressedBuffer = compressedBuffer;
        }

        public boolean coversFullCanvas() { return offsetX <= 0 && offsetY <= 0 && width >= canvasWidth && height >= canvasHeight; }
        public int getCanvasTotalPixels() { return canvasWidth * canvasHeight; }
        public boolean hasTransparentColor() { return graphicsControl != null && graphicsControl.isTransparentColor(); }
        public int getTransparentColorIndex() { return !hasTransparentColor() ? -1 : graphicsControl.getTransparentColorIndex() & 0xff; }

        static class Builder {
            private ParserState parserState;
            private Boolean isInterlaced;
            private Short offsetX, offsetY, width, height;
            private Byte minCodeSize;
            private byte[] compressedBuffer;

            public Builder withParserState(ParserState s) { this.parserState = s; return this; }
            public Builder withOffset(short x, short y) { this.offsetX = x; this.offsetY = y; return this; }
            public Builder withSize(short w, short h) { this.width = w; this.height = h; return this; }
            public Builder withInterlaced(boolean b) { this.isInterlaced = b; return this; }
            public Builder withMinCodeSize(byte b) { this.minCodeSize = b; return this; }
            public Builder withCompressedBuffer(byte[] compressedBuffer) { this.compressedBuffer = compressedBuffer; return this; }

            public KeyframeContext build() {
                if (parserState == null) throw new IllegalStateException("必须设置 ParserState");
                if (isInterlaced == null) throw new IllegalStateException("必须设置是否隔行扫描");
                if (offsetX == null || offsetY == null || width == null || height == null)
                    throw new IllegalStateException("必须设置帧偏移与尺寸");
                if (minCodeSize == null) throw new IllegalStateException("必须设置最小码长");
                if (compressedBuffer == null) throw new IllegalStateException("必须设置压缩数据");
                return new KeyframeContext(
                        parserState.getPreviousFrameInfo(), parserState.getGraphicsControlExtension(),
                        parserState.canvasWidth, parserState.canvasHeight,
                        parserState.getFrameCount() == 0, isInterlaced,
                        offsetX, offsetY, width, height, minCodeSize, compressedBuffer);
            }
        }
    }

    /**
     * 判断当前帧是否为关键帧。
     */
    private boolean determineIfKeyframe(KeyframeContext context) throws LzwCorruptedDataException {
        if (context.isFirstFrame) return true;
        if (context.graphicsControl == null) return false;
        if (context.graphicsControl.getDisplayMode() == DisplayMode.RESTORE_PREVIOUS) return false;
        if (!context.coversFullCanvas()) return false;
        if (!context.hasTransparentColor()) return true;

        int transIdx = context.getTransparentColorIndex();
        if (transIdx < 0 || transIdx >= 256) return true;
        if (context.prevFrameInfo == null) return false;

        if (context.prevFrameInfo.getDisplayMode() == DisplayMode.RESTORE_BACKGROUND) {
            FrameInfo prev = context.prevFrameInfo;
            return prev.getOffsetX() <= 0 && prev.getOffsetY() <= 0 &&
                    prev.getOffsetX() + prev.getWidth() >= context.canvasWidth &&
                    prev.getOffsetY() + prev.getHeight() >= context.canvasHeight;
        }

        byte[] pixelIndices = decompressor.decodeFrame(context.compressedBuffer, context.isInterlaced,
                context.minCodeSize, context.width, context.height);
        int total = context.getCanvasTotalPixels();
        if (pixelIndices.length < total) {
            PoolUtils.returnPixelIndices(poolManager, pixelIndices);
            return false;
        }
        for (int i = 0; i < total; i++) {
            if (transIdx == (pixelIndices[i] & 0xff)) {
                PoolUtils.returnPixelIndices(poolManager, pixelIndices);
                return false;
            }
        }
        PoolUtils.returnPixelIndices(poolManager, pixelIndices);
        return true;
    }

    // ===========================================================================
    // 资源关闭
    // ===========================================================================

    @Override
    public void close() throws IOException {
        closeState.checkClosed();
        closeState.markAsClosed();
        cancelCurrentTask();
        if (ownParseExecutor && !parseExecutor.isShutdown()) {
            parseExecutor.shutdownNow();
        }
    }

    private void cancelCurrentTask() {
        Future<?> active = currentTaskFuture.getAndSet(null);
        if (active != null && !active.isDone()) active.cancel(true);
    }
}