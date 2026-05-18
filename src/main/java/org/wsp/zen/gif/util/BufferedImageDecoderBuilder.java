package org.wsp.zen.gif.util;

import java.awt.image.BufferedImage;

import org.wsp.zen.cache.core.CacheManager;
import org.wsp.zen.cache.impl.DefaultCache;
import org.wsp.zen.cache.impl.DefaultCacheManager;
import org.wsp.zen.gif.impl.DefaultDecoder;
import org.wsp.zen.gif.impl.DefaultRendererFactory;
import org.wsp.zen.gif.model.CacheNames;
import org.wsp.zen.gif.model.DecodeFrame;
import org.wsp.zen.gif.model.FrameData;
import org.wsp.zen.gif.model.PoolNames;
import org.wsp.zen.handler.impl.DefaultHandlerFactory;
import org.wsp.zen.pool.core.ObjectPool;
import org.wsp.zen.pool.core.PoolManager;
import org.wsp.zen.pool.impl.DefaultBufferedImageFactory;
import org.wsp.zen.pool.impl.DefaultByteArrayFactory;
import org.wsp.zen.pool.impl.DefaultIntArrayFactory;
import org.wsp.zen.pool.impl.DefaultObjectPool;
import org.wsp.zen.pool.impl.DefaultPoolManager;

/**
 * 为 {@link BufferedImage} 类型的 GIF 解码提供“全预设”的
 * {@link DefaultDecoder.Builder} 快捷构造入口。
 *
 * <p>该类仅包含一个静态方法 {@link #newBuilder()}，它会创建一个已预先配置好
 * 所有必需底层组件（对象池、缓存管理器、渲染器/处理器工厂、类型令牌）的
 * {@code Builder}，用户可以直接调用 {@code build()} 得到可用的
 * {@link DefaultDecoder DefaultDecoder&lt;BufferedImage&gt;}，也可以在此基础上
 * 继续调用 {@code withXxx()} 方法覆盖默认值，保留完全的定制能力。</p>
 *
 * <h3>预设内容</h3>
 * <ul>
 *   <li>渲染器工厂：{@link DefaultRendererFactory}</li>
 *   <li>处理器工厂：{@link DefaultHandlerFactory}</li>
 *   <li>对象池管理器（PoolManager）：包含 byte[]、int[] 及 BufferedImage 的常用池</li>
 *   <li>缓存管理器（CacheManager）：已注册 FRAME_DATA、DECODE_FRAME、RENDER_FRAME 三个必需缓存</li>
 *   <li>图像类型令牌：{@code BufferedImage.class}</li>
 * </ul>
 * 其他配置（如窗口大小、超时、线程池、驱逐策略等）均沿用
 * {@link DefaultDecoder.Builder} 自身的默认值。
 *
 * <h3>方法命名说明</h3>
 * 静态方法取名为 {@code newBuilder()} 而非 {@code builder()}，
 * 主要是为了遵循 Java 生态中“静态工厂创建 Builder”的常见惯例
 * （例如 {@code HttpClient.newBuilder()}），语义上强调“新建一个 Builder
 * 实例”，避免了与实例方法 {@code builder()} 的可能混淆。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 最简用法：一行构建，立刻可用
 * DefaultDecoder<BufferedImage> decoder =
 *     BufferedImageDecoderBuilder.newBuilder().build();
 *
 * // 2. 覆盖缓存管理器（使用自定义缓存）
 * DefaultDecoder<BufferedImage> decoder =
 *     BufferedImageDecoderBuilder.newBuilder()
 *         .withCacheManager(myCacheManager)
 *         .build();
 *
 * // 3. 进一步定制窗口、线程池等
 * DefaultDecoder<BufferedImage> decoder =
 *     BufferedImageDecoderBuilder.newBuilder()
 *         .withForwardWindowSize(3)
 *         .withLoadTimeout(10)
 *         .withReadExecutor(customExecutor)
 *         .build();
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 */
public final class BufferedImageDecoderBuilder {

    private BufferedImageDecoderBuilder() {
        // 工具类，禁止实例化
    }

    /**
     * 返回一个“全预设”的 {@link DefaultDecoder.Builder DefaultDecoder.Builder&lt;BufferedImage&gt;}，
     * 预置了运行解码器所需的全部默认组件。
     *
     * <p>调用者可以在此基础上继续配置其他参数，也可以直接调用
     * {@link DefaultDecoder.Builder#build() build()} 获得解码器实例。</p>
     *
     * @return 预设完整的 Builder，随时可构建或继续定制
     */
    public static DefaultDecoder.Builder<BufferedImage> newBuilder() {
        // 1. 构建默认对象池管理器
        PoolManager poolManager = createDefaultPoolManager();

        // 2. 构建默认缓存管理器
        CacheManager cacheManager = createDefaultCacheManager();

        // 3. 组装 Builder 并填入所有无默认值的必填项
        return new DefaultDecoder.Builder<BufferedImage>()
                .withRendererFactory(new DefaultRendererFactory())
                .withHandlerFactory(new DefaultHandlerFactory())
                .withPoolManager(poolManager)
                .withRenderImageType(BufferedImage.class)
                .withCacheManager(cacheManager);
    }

    // ---------- 内部组件工厂 ----------

    /**
     * 创建并注册一个包含常用对象池的默认 {@link PoolManager}。
     *
     * <p>对象池列表：</p>
     * <ul>
     *   <li>byte[] 压缩数据池</li>
     *   <li>byte[] 像素索引池</li>
     *   <li>byte[] 颜色表字节池</li>
     *   <li>int[] LZW 字典数组池</li>
     *   <li>int[] 行像素数组池</li>
     *   <li>int[] 颜色表整数池</li>
     *   <li>BufferedImage 渲染图像池</li>
     * </ul>
     *
     * @return 已注册所有默认对象池的 PoolManager 实例
     */
    private static PoolManager createDefaultPoolManager() {
        DefaultPoolManager poolManager = new DefaultPoolManager();
        int poolCapacity = 2;  // 每个池的默认容量，可根据实际场景调整

        poolManager.register(byte[].class, PoolNames.COMPRESSED_DATA,
                new DefaultObjectPool<>(poolCapacity, new DefaultByteArrayFactory()));
        poolManager.register(byte[].class, PoolNames.PIXEL_INDICES,
                new DefaultObjectPool<>(poolCapacity, new DefaultByteArrayFactory()));
        poolManager.register(byte[].class, PoolNames.COLOR_TABLE_BYTES,
                new DefaultObjectPool<>(poolCapacity, new DefaultByteArrayFactory()));
        poolManager.register(int[].class, PoolNames.DICT_INT_ARRAY,
                new DefaultObjectPool<>(poolCapacity, new DefaultIntArrayFactory()));
        poolManager.register(int[].class, PoolNames.ROW_INT_ARRAY,
                new DefaultObjectPool<>(poolCapacity, new DefaultIntArrayFactory()));
        poolManager.register(int[].class, PoolNames.COLOR_TABLE_INT,
                new DefaultObjectPool<>(poolCapacity, new DefaultIntArrayFactory()));

        // 渲染图像池
        ObjectPool<BufferedImage> renderPool = new DefaultObjectPool<>(poolCapacity, new DefaultBufferedImageFactory());
        poolManager.register(BufferedImage.class, PoolNames.RENDER_IMAGE, renderPool);

        return poolManager;
    }

    /**
     * 创建并注册一个包含必需缓存的默认 {@link CacheManager}。
     *
     * <p>注册的缓存包括：</p>
     * <ul>
     *   <li>{@value CacheNames#FRAME_DATA_CACHE} —— 帧数据缓存</li>
     *   <li>{@value CacheNames#DECODE_FRAME_CACHE} —— 解码帧缓存</li>
     *   <li>{@value CacheNames#RENDER_FRAME_CACHE} —— 渲染帧缓存</li>
     * </ul>
     *
     * @return 已注册三个必需缓存的 CacheManager 实例
     */
    private static CacheManager createDefaultCacheManager() {
        CacheManager cacheManager = new DefaultCacheManager();
        cacheManager.register(CacheNames.FRAME_DATA_CACHE, new DefaultCache<Integer, FrameData>());
        cacheManager.register(CacheNames.DECODE_FRAME_CACHE, new DefaultCache<Integer, DecodeFrame>());
        cacheManager.register(CacheNames.RENDER_FRAME_CACHE, new DefaultCache<Integer, BufferedImage>());
        return cacheManager;
    }
}