package org.wsp.zen.gif.core;

import org.wsp.zen.gif.extension.Extension;
import org.wsp.zen.gif.extension.GraphicsControlExtension;

/**
 * GIF 解析过程中处理扩展块的回调接口。
 * <p>
 * 在解码器解析 GIF 数据流时，每遇到一个扩展块（Extension Block）就会调用相应的回调方法。
 * 该接口允许调用者获取扩展块中的元数据，例如注释、应用扩展或图形控制参数等。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Decoder<BufferedImage> decoder = new GifDecoder();
 * decoder.setExtensionCallback(new ExtensionCallback() {
 *     @Override
 *     public void onGlobalExtension(Extension extension) {
 *         if (extension instanceof CommentExtension) {
 *             System.out.println("注释: " + ((CommentExtension) extension).getText());
 *         }
 *     }
 *
 *     @Override
 *     public void onGraphicsControlExtension(GraphicsControlExtension graphicsControl) {
 *         System.out.println("延迟时间: " + graphicsControl.getDelayTime() + "ms");
 *     }
 * });
 * decoder.load("animation.gif");
 * }</pre>
 *
 * <p><b>回调顺序：</b>
 * 扩展块可能出现在 GIF 数据流的多个位置（全局扩展块在图像块之前，局部扩展块与图像数据交错）。
 * 实现类应保证按照数据流中的原始顺序调用这些回调。
 *
 * <p><b>线程安全性：</b>
 * 回调方法通常由解码器的工作线程直接调用，因此实现类应确保回调方法是线程安全的，
 * 且避免执行耗时操作以免阻塞解析过程。如需跨线程传递数据，应使用线程安全的数据结构。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see Extension
 * @see GraphicsControlExtension
 * @see org.wsp.zen.gif.extension.CommentExtension
 * @see org.wsp.zen.gif.extension.PlainTextExtension
 * @see org.wsp.zen.gif.extension.ApplicationExtension
 */
public interface ExtensionCallback {

    /**
     * 当解析到任意全局扩展块（或非特定类型的扩展块）时调用。
     * <p>
     * 此方法接收所有未被更具体回调单独处理的扩展块，包括：
     * <ul>
     *   <li>注释扩展（{@code CommentExtension}）</li>
     *   <li>纯文本扩展（{@code GifPlainTextExtension}）</li>
     *   <li>应用扩展（{@code ApplicationExtension}）</li>
     *   <li>任何未知或保留的扩展类型</li>
     * </ul>
     * 注意：图形控制扩展（Graphics Control Extension）通常不会触发此方法，
     * 因为其有专门的回调 {@link #onGraphicsControlExtension}。
     * </p>
     *
     * @param extension 解析成功的扩展块对象，不能为 {@code null}。
     *                  调用者可以通过 {@code instanceof} 判断具体子类型并安全转换。
     */
    void onGlobalExtension(Extension extension);

    /**
     * 当解析到图形控制扩展块（Graphics Control Extension）时调用。
     * <p>
     * 图形控制扩展块紧跟在图像数据之前，用于指定当前帧的延迟时间、透明色索引、处置方式等。
     * 此回调使得调用者能够获取每一帧的显示参数，通常用于实现动画播放逻辑。
     * </p>
     *
     * @param graphicsControl 图形控制扩展对象，包含延迟时间、透明标志、处置方法等信息，不能为 {@code null}
     */
    void onGraphicsControlExtension(GraphicsControlExtension graphicsControl);

    /**
     * 当遇到未知类型（或解码器未识别）的扩展块时调用。
     * <p>
     * 默认实现会在标准错误流打印一条警告信息，格式为：
     * {@code "跳过未知扩展类型: 0xXX"}，其中 {@code XX} 为扩展块的十六进制标签。
     * 子类可覆盖此方法以提供自定义处理（例如记录日志、忽略特定类型或抛出异常）。
     * </p>
     *
     * @param extType 扩展块的标签字节（Label），范围通常为 {@code 0x01} - {@code 0xFF}，
     *                例如 {@code 0xFE} 表示注释扩展，但若解码器不认识该类型则传入原始标签值。
     */
    default void onUnknownExtension(int extType) {
        System.err.println("跳过未知扩展类型: 0x" + Integer.toHexString(extType));
    }
}