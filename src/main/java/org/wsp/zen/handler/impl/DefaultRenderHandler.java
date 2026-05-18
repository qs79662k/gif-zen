package org.wsp.zen.handler.impl;

import java.util.Objects;

import org.wsp.zen.cache.core.Cache;
import org.wsp.zen.gif.core.Renderer;
import org.wsp.zen.gif.model.DecodeFrame;
import org.wsp.zen.gif.model.RenderContext;
import org.wsp.zen.handler.exception.ProcessingException;
import org.wsp.zen.handler.core.Handler;
import org.wsp.zen.handler.model.HandlerContext;

/**
 * 默认的渲染处理器，对应 GIF 处理流水线的第三阶段（最终阶段）。
 * <p>
 * 该处理器负责将解码后的帧数据与参考帧（如前一帧或背景）合成，输出最终渲染结果对象。
 * 渲染结果会通过渲染结果缓存进行复用，避免对同一帧的重复渲染。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该处理器本身是无状态的（依赖的组件需各自保证线程安全），因此可在多线程环境中安全复用。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Renderer<BufferedImage> renderer = ...;
 * Handler<DecodeFrame> decodeHandler = ...;
 * Cache<Integer, BufferedImage> renderCache = ...;
 *
 * Handler<BufferedImage> renderHandler = new DefaultRenderHandler<>(
 *         renderer, decodeHandler, renderCache);
 *
 * HandlerContext context = ...;
 * BufferedImage frame = renderHandler.process(context);
 * }</pre>
 *
 * @param <T> 渲染结果类型（例如 {@link java.awt.image.BufferedImage}）
 * @author wsp
 * @version 1.0
 * @see Handler
 * @see Renderer
 * @see RenderContext
 */
public class DefaultRenderHandler<T> implements Handler<T> {

    private final Renderer<T> renderer;
    private final Handler<DecodeFrame> decodeHandler;
    private final Cache<Integer, T> renderFrameCache;

    /**
     * 构造渲染处理器。
     *
     * @param renderer         渲染器，负责将解码帧转换为目标图像类型，不能为 {@code null}
     * @param decodeHandler    前一阶段（解码处理器），用于获取解码后的帧数据，不能为 {@code null}
     * @param renderFrameCache 渲染结果缓存，用于复用已渲染的帧，不能为 {@code null}
     * @throws NullPointerException 如果任一参数为 {@code null}
     */
    public DefaultRenderHandler(
            Renderer<T> renderer,
            Handler<DecodeFrame> decodeHandler,
            Cache<Integer, T> renderFrameCache) {
        this.renderer = Objects.requireNonNull(renderer, "渲染器不能为 null");
        this.decodeHandler = Objects.requireNonNull(decodeHandler, "解码处理器不能为 null");
        this.renderFrameCache = Objects.requireNonNull(renderFrameCache, "渲染结果缓存不能为 null");
    }

    /**
     * 处理渲染请求，返回指定帧的渲染结果。
     * <p>
     * 该方法首先尝试从 {@code renderFrameCache} 获取已缓存的渲染结果；
     * 若未命中，则执行以下步骤：
     * <ol>
     *   <li>若存在参考帧索引，从缓存中获取参考帧（若缺失则抛出异常）</li>
     *   <li>通过 {@code decodeHandler} 获取当前帧的解码数据</li>
     *   <li>构建 {@link RenderContext} 并调用 {@code renderer} 执行渲染</li>
     *   <li>将渲染结果存入缓存</li>
     * </ol>
     * </p>
     *
     * @param context 处理上下文，包含当前帧信息、参考帧索引、前一帧信息等，不能为 {@code null}
     * @return 渲染后的结果对象
     * @throws ProcessingException 如果参考帧缺失、解码或渲染过程中发生错误
     * @throws NullPointerException 如果 {@code context} 为 {@code null}
     */
    @Override
    public T process(HandlerContext context) {
        Objects.requireNonNull(context, "处理器上下文对象不能为 null");
        
        int key = context.currentFrameIndex;
        // 优先从缓存获取，未命中则执行渲染并缓存
        return renderFrameCache.fetchOrCompute(key, k -> {
            try {
                // 获取参考帧（用于合成，例如前一帧或背景）
                T referenceFrame = null;
                int referenceFrameIndex = context.referenceFrameIndex;
                if (referenceFrameIndex > -1) {
                    referenceFrame = renderFrameCache.get(referenceFrameIndex);
                    if (referenceFrame == null) {
                        throw new ProcessingException(
                                "参考帧[" + referenceFrameIndex + "]未找到，无法渲染帧[" + key + "]");
                    }
                }

                // 获取解码后的帧数据
                DecodeFrame decodedFrame = decodeHandler.process(context);
                // 构建渲染上下文并执行渲染
                RenderContext<T> renderContext = new RenderContext.Builder<T>()
                        .withDecodeFrame(decodedFrame)
                        .withCurrentFrameInfo(context.currentFrameInfo)
                        .withReferenceFrame(referenceFrame)
                        .withBackgroundColor(context.getBackgroundColor())
                        .withPrevFrameInfo(context.prevFrameInfo)
                        .build();
    
                return renderer.render(renderContext);
            } catch (Exception e) {
                throw new ProcessingException("帧[" + key + "]渲染失败", e);
            }
        });
    }
}