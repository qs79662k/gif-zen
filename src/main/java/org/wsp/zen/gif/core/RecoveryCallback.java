package org.wsp.zen.gif.core;

import org.wsp.zen.gif.extension.Extension;
import org.wsp.zen.gif.extension.GraphicsControlExtension;
import org.wsp.zen.gif.model.ImageDescriptor;

/**
 * GIF 解析错误恢复过程中的回调接口，用于通知成功恢复的数据块。
 * <p>
 * 当解析器在遇到数据损坏或格式错误时，会尝试通过扫描下一个有效数据块的起始标识来恢复解析。
 * 一旦成功定位到某个有效块（如扩展块、图像描述符等），解析器会继续解析该块的内容，
 * 并通过本接口的回调方法将恢复后的数据通知调用方。
 * </p>
 *
 * <p><b>回调时机：</b>
 * 这些回调仅在错误恢复流程中被调用，而非正常解析流程。正常解析应使用 {@link ParseCallback}。
 * 实现类可通过这些回调获知哪些数据块是通过容错机制恢复出来的，从而进行特殊处理或记录日志。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 回调方法由解析器的工作线程直接调用，因此实现类应确保线程安全，且避免执行耗时操作以免阻塞恢复流程。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Parser parser = new MyGifParser();
 * parser.setRecoveryCallback(new RecoveryCallback() {
 *     @Override
 *     public void onGlobalExtensionRecovered(Extension extension) {
 *         System.out.println("恢复全局扩展块: " + extension.getClass().getSimpleName());
 *     }
 *
 *     @Override
 *     public void onGraphicsControlExtensionRecovered(GraphicsControlExtension graphicsControl) {
 *         System.out.println("恢复图形控制扩展，延迟: " + graphicsControl.getDelayTime() + "ms");
 *     }
 *
 *     @Override
 *     public void onImageDescriptorRecovered(ImageDescriptor descriptor) {
 *         System.out.println("恢复图像描述符: " + descriptor.width() + "x" + descriptor.height());
 *     }
 * });
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see ParseCallback
 * @see Extension
 * @see GraphicsControlExtension
 * @see ImageDescriptor
 */
public interface RecoveryCallback {

    /**
     * 当从错误中成功恢复并解析出一个全局扩展块时调用。
     * <p>
     * 全局扩展块通常包括注释扩展、纯文本扩展或应用扩展等。
     * 此回调允许调用方获取恢复后的扩展块内容，便于补全 GIF 元数据。
     * </p>
     *
     * @param extension 成功恢复并解析出的扩展块对象，不能为 {@code null}
     */
    void onGlobalExtensionRecovered(Extension extension);

    /**
     * 当从错误中成功恢复并解析出一个图形控制扩展块时调用。
     * <p>
     * 图形控制扩展包含帧延迟时间、透明色索引、处置方式等关键动画参数。
     * 恢复该扩展有助于正确渲染后续帧。
     * </p>
     *
     * @param graphicsControl 成功恢复并解析出的图形控制扩展对象，不能为 {@code null}
     */
    void onGraphicsControlExtensionRecovered(GraphicsControlExtension graphicsControl);

    /**
     * 当从错误中成功恢复并解析出一个图像描述符时调用。
     * <p>
     * 图像描述符定义了帧的尺寸、位置以及局部调色板信息。
     * 恢复该描述符意味着后续的图像数据流可以被正确解析。
     * </p>
     *
     * @param descriptor 成功恢复并解析出的图像描述符对象，不能为 {@code null}
     */
    void onImageDescriptorRecovered(ImageDescriptor descriptor);

    /**
     * 当恢复流程未能找到任何有效数据块时调用。
     * <p>
     * 默认实现为空，子类可覆盖以执行特定操作（如记录警告、提前终止解析等）。
     * 此回调通常表示剩余数据已完全损坏或到达流末尾。
     * </p>
     */
    default void onNoValidDataRecovered() {}
}