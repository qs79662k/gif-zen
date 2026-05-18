package org.wsp.zen.handler.impl;

import java.awt.image.BufferedImage;
import java.util.Objects;

import org.wsp.zen.cache.core.Cache;
import org.wsp.zen.gif.core.LzwDecompressor;
import org.wsp.zen.gif.core.Renderer;
import org.wsp.zen.gif.model.DecodeFrame;
import org.wsp.zen.gif.model.FrameData;
import org.wsp.zen.handler.core.Handler;
import org.wsp.zen.handler.core.HandlerFactory;
import org.wsp.zen.mapping.core.FileMappingManager;
import org.wsp.zen.pool.core.PoolManager;

/**
 * 默认的处理器工厂实现，用于创建 GIF 处理流水线各阶段的处理器实例。
 * <p>
 * 该工厂针对 {@link BufferedImage} 渲染目标类型，分别提供：
 * <ul>
 *   <li>读取处理器（{@link DefaultReadHandler}）</li>
 *   <li>解码处理器（{@link DefaultDecodeHandler}）</li>
 *   <li>渲染处理器（{@link DefaultRenderHandler}）</li>
 * </ul>
 * 所有创建的处理器均依赖缓存、解压器、渲染器等组件，形成完整的三级流水线。
 * </p>
 *
 * <p><b>资源管理：</b>
 * 读取处理器利用对象池管理器复用临时缓冲区，若管理器为 {@code null} 则自动降级为新建数组。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该工厂是无状态的，因此是线程安全的。所有工厂方法均可被多线程并发调用。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see HandlerFactory
 * @see DefaultReadHandler
 * @see DefaultDecodeHandler
 * @see DefaultRenderHandler
 */
public class DefaultHandlerFactory implements HandlerFactory<BufferedImage> {

    /**
     * 创建“读取”处理器实例，注入对象池管理器以复用临时缓冲区。
     *
     * @param mapperManager 文件映射管理器，不能为 {@code null}
     * @param readCache     帧原始数据缓存，不能为 {@code null}
     * @param poolManager   对象池管理器，可为 {@code null} 以使用无池模式
     * @return 读取处理器实例
     * @throws NullPointerException 如果 {@code mapperManager} 或 {@code readCache} 为 {@code null}
     */
    @Override
    public Handler<FrameData> createReadHandler(FileMappingManager mapperManager,
                                                Cache<Integer, FrameData> readCache,
                                                PoolManager poolManager) {
        Objects.requireNonNull(readCache, "读取帧数据缓存不能为 null");
        return new DefaultReadHandler(mapperManager, readCache, poolManager);
    }

    @Override
    public Handler<DecodeFrame> createDecodeHandler(LzwDecompressor lzwDecoder,
                                                    Handler<FrameData> readHandler,
                                                    Cache<Integer, DecodeFrame> decodeCache) {
        return new DefaultDecodeHandler(lzwDecoder, readHandler, decodeCache);
    }

    @Override
    public Handler<BufferedImage> createRenderHandler(Renderer<BufferedImage> renderer,
                                                      Handler<DecodeFrame> decodeHandler,
                                                      Cache<Integer, BufferedImage> renderCache) {
        return new DefaultRenderHandler<>(renderer, decodeHandler, renderCache);
    }
}