package org.wsp.zen.gif.core;

import org.wsp.zen.pool.core.PoolManager;

/**
 * GIF 渲染器工厂接口，用于创建 {@link Renderer} 实例。
 * <p>
 * 渲染器负责将解码后的 GIF 帧数据转换为具体的图像对象（如 {@link java.awt.image.BufferedImage}、
 * {@link android.graphics.Bitmap} 等）。工厂模式允许不同的实现按需提供渲染器，
 * 并通过 {@link PoolManager} 动态获取渲染帧对象池，以复用图像对象，减少内存分配开销，
 * 提升动画播放性能。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * // 准备对象池管理器（注册渲染帧对象池）
 * PoolManager poolManager = new DefaultPoolManager();
 * poolManager.register(…, "gif-render-image", myRenderFramePool);
 *
 * // 创建渲染器（传入画布宽高）
 * RendererFactory<BufferedImage> factory = new DefaultRendererFactory();
 * Renderer<BufferedImage> renderer = factory.create(poolManager, canvasWidth, canvasHeight);
 *
 * // 使用 renderer 进行帧渲染
 * BufferedImage frame = renderer.render(context);
 * }</pre>
 *
 * <p><b>对象池的作用：</b>
 * 对于频繁渲染帧的场景（例如动画播放），每次渲染都创建新图像对象会导致 GC 压力。
 * 通过对象池复用相同尺寸和格式的图像缓冲区，可以显著降低内存抖动。渲染器内部
 * 通过 {@link PoolManager} 按名称获取渲染帧池（名称通常为 {@code "gif-render-image"}），
 * 无需在构造时硬绑定某个池实例。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 工厂实现类应保证线程安全，因为 {@link #create(PoolManager, int, int)} 方法可能被多个线程并发调用。
 * 通常，无状态工厂是天然线程安全的。
 * </p>
 *
 * @param <T> 渲染结果的图像类型，例如 {@link java.awt.image.BufferedImage}、
 *            {@link javafx.scene.image.Image} 或 {@link android.graphics.Bitmap}
 * @author wsp
 * @version 1.0
 * @see Renderer
 * @see PoolManager
 */
public interface RendererFactory<T> {

    /**
     * 创建一个新的渲染器实例。
     * <p>
     * 渲染器内部可以通过传入的 {@link PoolManager} 按名称获取渲染帧对象池
     * （例如 {@code "gif-render-image"}），以实现图像对象的内存复用。
     * 同时，渲染器需要知道画布的宽度和高度，以便创建或调整渲染帧图像的大小。
     * </p>
     *
     * @param poolManager  对象池管理器，用于按需获取渲染帧对象池，不能为 {@code null}
     * @param canvasWidth  画布宽度（像素），必须大于 0
     * @param canvasHeight 画布高度（像素），必须大于 0
     * @return 创建好的渲染器实例，不为 {@code null}
     * @throws NullPointerException     如果 {@code poolManager} 为 {@code null}
     * @throws IllegalArgumentException 如果宽高不合法
     */
    Renderer<T> create(PoolManager poolManager, int canvasWidth, int canvasHeight);
}