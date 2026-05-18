package org.wsp.zen.gif.impl;

import java.awt.image.BufferedImage;

import org.wsp.zen.gif.core.Renderer;
import org.wsp.zen.gif.core.RendererFactory;
import org.wsp.zen.pool.core.PoolManager;

/**
 * 默认的 GIF 渲染器工厂实现，用于创建渲染 {@link BufferedImage} 类型的渲染器实例。
 * <p>
 * 该工厂创建 {@link DefaultRenderer} 实例，渲染器内部会通过传入的 {@link PoolManager}
 * 按名称获取渲染帧对象池，并根据画布宽高创建或复用 {@link BufferedImage} 对象，
 * 以减少动画播放过程中的内存分配开销。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该工厂是无状态的，因此是线程安全的。{@link #create(PoolManager, int, int)} 方法
 * 可以被多个线程并发调用。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * // 准备对象池管理器（已注册渲染帧对象池）
 * PoolManager poolManager = new DefaultPoolManager();
 * poolManager.register(…, "gif-render-image", new DefaultObjectPool<>(...));
 *
 * // 创建渲染器（指定画布宽高）
 * RendererFactory<BufferedImage> factory = new DefaultRendererFactory();
 * Renderer<BufferedImage> renderer = factory.create(poolManager, 640, 480);
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see RendererFactory
 * @see DefaultRenderer
 * @see PoolManager
 */
public class DefaultRendererFactory implements RendererFactory<BufferedImage> {

    /**
     * 创建一个新的 {@link Renderer} 实例，用于将 GIF 帧渲染为 {@link BufferedImage}。
     *
     * @param poolManager  对象池管理器，用于按需获取渲染帧对象池，不能为 {@code null}
     * @param canvasWidth  画布宽度（像素），必须大于 0
     * @param canvasHeight 画布高度（像素），必须大于 0
     * @return 新的渲染器实例，不为 {@code null}
     * @throws NullPointerException     如果 {@code poolManager} 为 {@code null}
     * @throws IllegalArgumentException 如果宽高不合法（<=0）
     */
    @Override
    public Renderer<BufferedImage> create(PoolManager poolManager, int canvasWidth, int canvasHeight) {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            throw new IllegalArgumentException(
                "画布宽高必须大于 0，当前值：width=" + canvasWidth + ", height=" + canvasHeight);
        }
        
        return new DefaultRenderer(poolManager, canvasWidth, canvasHeight);
    }
}