package org.wsp.zen.handler.core;

import org.wsp.zen.handler.model.HandlerContext;

/**
 * GIF 处理器接口，用于定义对 GIF 数据（如帧、扩展块或整个动画）的处理逻辑。
 * <p>
 * 该接口采用责任链或流水线模式，允许将不同的处理步骤（如读取、解码、渲染）
 * 封装为独立的处理器，并通过 {@link HandlerContext} 传递上下文信息。
 * 处理器通常是无状态的，可在多个上下文间安全复用。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 实现类应保证线程安全，因为 {@link #process(HandlerContext)} 方法可能被多个线程并发调用。
 * 通常，无状态处理器是天然线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Handler<BufferedImage> renderHandler = context -> {
 *     DecodeFrame frame = context.getCurrentDecodeFrame();
 *     return renderer.render(frame);
 * };
 *
 * Handler<Void> cacheHandler = context -> {
 *     cache.put(context.getCurrentFrameIndex(), context.getRenderedImage());
 *     return null;
 * };
 * }</pre>
 *
 * @param <T> 处理结果的类型（例如 {@link java.awt.image.BufferedImage}、{@link Void}、帧索引等）
 * @author wsp
 * @version 1.0
 * @see HandlerContext
 */
public interface Handler<T> {

    /**
     * 执行处理逻辑，基于给定的上下文返回处理结果。
     * <p>
     * 上下文对象包含了处理所需的所有信息，如当前帧索引、帧元数据、解码后的数据、
     * 参考帧、配置参数等。具体需要哪些字段由处理器实现自行决定。
     * </p>
     *
     * @param context 处理上下文，包含当前帧、解码状态、配置参数等信息，不能为 {@code null}
     * @return 处理结果（类型为 {@code T}），可能为 {@code null}（如果处理器不需要返回值）
     * @throws NullPointerException     如果 {@code context} 为 {@code null}
     * @throws org.wsp.zen.handle.exception.ProcessingException 如果处理过程中发生业务错误
     * @throws RuntimeException         如果发生其他未预期的运行时错误
     */
    T process(HandlerContext context);
}