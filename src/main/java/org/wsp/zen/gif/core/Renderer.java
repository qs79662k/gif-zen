package org.wsp.zen.gif.core;

import org.wsp.zen.gif.model.RenderContext;

/**
 * GIF 帧渲染器接口，负责将解码后的帧数据渲染为目标图像类型。
 * <p>
 * 渲染器接收一个 {@link RenderContext} 上下文对象，其中包含当前帧的解码数据、
 * 帧元信息、参考帧（前一帧图像）以及其他渲染参数。实现类根据上下文中的配置
 * （如处置方式、透明色处理、叠加模式等）生成最终的帧图像。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Renderer<BufferedImage> renderer = new AwtGifRenderer();
 * RenderContext<BufferedImage> context = new RenderContext.Builder<BufferedImage>()
 *         .withDecodedFrame(decodedFrame)           // DecodeFrame 包含 pixelIndices 和 colorTable
 *         .withCurrentFrameInfo(frameInfo)          // FrameInfo 提供尺寸、偏移、处置方式等
 *         .withReferenceFrame(previousImage)        // 前一帧渲染结果
 *         .withBackgroundColor(0xFFFFFFFF)          // 背景色（ARGB）
 *         .build();
 *
 * BufferedImage frame = renderer.render(context);
 * }</pre>
 *
 * <p><b>线程安全性：</b>
 * 实现类应保证线程安全，因为渲染操作可能由多个线程并发调用（例如异步解码多帧）。
 * 通常，无状态的渲染器是天然线程安全的；若有内部缓存或状态，需做好同步控制。
 * </p>
 *
 * <p><b>参考帧处理：</b>
 * 对于首帧（{@code context.referenceFrame == null}），实现类应直接从调色板和像素索引构建新图像。
 * 对于后续帧，应根据处置方式（Disposal Method）决定如何处理参考帧：
 * <ul>
 *   <li>处置方式 0（未指定）：通常叠加绘制</li>
 *   <li>处置方式 1（不处置）：保留前一帧，将当前帧叠加于其上</li>
 *   <li>处置方式 2（恢复为背景色）：将受影响的区域恢复为背景色</li>
 *   <li>处置方式 3（恢复为先前状态）：恢复为叠加前的图像状态</li>
 * </ul>
 * </p>
 *
 * @param <T> 渲染输出的图像类型，通常为 {@link java.awt.image.BufferedImage} 或 {@link android.graphics.Bitmap}
 * @author wsp
 * @version 1.0
 * @see RenderContext
 */
public interface Renderer<T> {

    /**
     * 将解码帧渲染为目标类型 {@code T} 的图像结果。
     * <p>
     * 渲染过程基于提供的上下文对象，该对象封装了当前解码帧的像素索引、调色板、
     * 参考帧（前一帧渲染结果）及其他渲染参数。具体渲染行为（如叠加方式、透明度处理等）
     * 由实现类根据 GIF 规范定义。
     * </p>
     *
     * @param context 渲染上下文，包含当前帧数据、参考帧及渲染配置，不能为 {@code null}
     * @return 渲染完成后的 {@code T} 类型结果（例如 {@link android.graphics.Bitmap}、{@link java.awt.image.BufferedImage} 等）
     * @throws NullPointerException     如果 {@code context} 为 {@code null}
     * @throws IllegalArgumentException 如果 {@code context} 中的数据不合法（如帧尺寸超出范围、颜色索引无效等）
     */
    T render(RenderContext<T> context);
}