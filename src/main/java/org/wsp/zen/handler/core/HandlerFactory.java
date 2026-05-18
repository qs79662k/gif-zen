package org.wsp.zen.handler.core;

import org.wsp.zen.cache.core.Cache;
import org.wsp.zen.gif.core.LzwDecompressor;
import org.wsp.zen.gif.core.Renderer;
import org.wsp.zen.gif.model.DecodeFrame;
import org.wsp.zen.gif.model.FrameData;
import org.wsp.zen.mapping.core.FileMappingManager;
import org.wsp.zen.pool.core.PoolManager;

/**
 * GIF 处理器工厂接口，用于创建处理流水线中的各个环节处理器。
 * <p>
 * 该工厂定义了三个层次的处理器创建方法，分别对应 GIF 处理流水线的三个阶段：
 * </p>
 * <ol>
 *   <li><b>读取阶段（Read）</b>：从文件映射或缓存中获取原始帧数据（{@link FrameData}）。</li>
 *   <li><b>解码阶段（Decode）</b>：对原始数据进行 LZW 解压，得到解码后的帧数据（{@link DecodeFrame}）。</li>
 *   <li><b>渲染阶段（Render）</b>：将解码后的帧渲染为目标类型 {@code T}（例如 {@link java.awt.image.BufferedImage}）。</li>
 * </ol>
 *
 * <p><b>线程安全性：</b>
 * 实现类应保证线程安全，因为工厂方法可能被多个线程并发调用。通常，无状态工厂是天然线程安全的。
 * </p>
 *
 * @param <T> 渲染结果的目标类型（如图像类型）
 * @author wsp
 * @version 1.0
 * @see Handler
 * @see FrameData
 * @see DecodeFrame
 */
public interface HandlerFactory<T> {

    /**
     * 创建“读取”处理器，负责获取原始帧数据（压缩前的 GIF 帧块）。
     * <p>
     * 读取处理器内部通常会：
     * <ul>
     *   <li>首先检查 {@code readCache} 是否已缓存指定帧的原始数据；</li>
     *   <li>若未命中，则通过 {@code mapperManager} 从文件/内存映射中读取；</li>
     *   <li>将读取的结果存入缓存供后续复用。</li>
     * </ul>
     * </p>
     *
     * @param mapperManager 文件映射管理器，用于定位和读取 GIF 原始数据块，不能为 {@code null}
     * @param readCache     帧原始数据的缓存（键为帧索引，值为 {@link FrameData}），不能为 {@code null}
     * @param poolManager   对象池管理器，用于复用临时字节数组；可为 {@code null} 表示降级为直接新建
     * @return 读取处理器实例，不为 {@code null}
     * @throws NullPointerException 如果 {@code mapperManager} 或 {@code readCache} 为 {@code null}
     */
    Handler<FrameData> createReadHandler(
            FileMappingManager mapperManager,
            Cache<Integer, FrameData> readCache,
            PoolManager poolManager);

    /**
     * 创建“解码”处理器，负责对原始帧数据进行 LZW 解压。
     *
     * @param lzwDecoder    LZW 解压缩器，用于解码压缩的图像数据，不能为 {@code null}
     * @param readProcessor 读取处理器（通常由 {@link #createReadHandler} 创建），不能为 {@code null}
     * @param decodeCache   解码后帧数据的缓存（键为帧索引，值为 {@link DecodeFrame}），不能为 {@code null}
     * @return 解码处理器实例，不为 {@code null}
     * @throws NullPointerException 如果任一参数为 {@code null}
     */
    Handler<DecodeFrame> createDecodeHandler(
            LzwDecompressor lzwDecoder,
            Handler<FrameData> readProcessor,
            Cache<Integer, DecodeFrame> decodeCache);

    /**
     * 创建“渲染”处理器，负责将解码后的帧转换为最终图像对象。
     *
     * @param renderer        渲染器，负责将解码帧转换为目标图像类型，不能为 {@code null}
     * @param decodeProcessor 解码处理器（通常由 {@link #createDecodeHandler} 创建），不能为 {@code null}
     * @param renderCache     渲染结果的缓存（键为帧索引，值为渲染后的图像对象），不能为 {@code null}
     * @return 渲染处理器实例，不为 {@code null}
     * @throws NullPointerException 如果任一参数为 {@code null}
     */
    Handler<T> createRenderHandler(
            Renderer<T> renderer,
            Handler<DecodeFrame> decodeProcessor,
            Cache<Integer, T> renderCache);
}