package org.wsp.zen.gif.core;

import org.wsp.zen.gif.extension.Extension;
import org.wsp.zen.gif.model.FrameInfo;
import org.wsp.zen.gif.model.Header;

/**
 * GIF 解析过程回调接口，用于接收解析器在不同阶段产生的事件通知。
 * <p>
 * 在解码器解析 GIF 数据流时，会依次回调本接口的方法，向调用者报告解析进度、元数据、
 * 帧信息以及可能发生的错误。调用者可通过实现本接口来监控解析状态、收集信息或处理异常。
 * </p>
 *
 * <p><b>回调顺序：</b>
 * 正常解析流程下，回调顺序如下：
 * <ol>
 *   <li>{@link #onHeader(Header)} — 头部信息解析完成</li>
 *   <li>{@link #onGlobalExtensionParsed(Extension)} — 每遇到一个全局扩展块调用一次（可能多次）</li>
 *   <li>{@link #onFrameParsed(FrameInfo)} — 每完成一帧的元数据解析调用一次（可能多次）</li>
 *   <li>{@link #onParseComplete()} — 全部数据解析完成</li>
 * </ol>
 * 若解析过程中发生错误，则可能触发：
 * <ul>
 *   <li>{@link #onRecoveryAttempt(int, int, Exception)} — 每次尝试恢复时调用（默认空实现）</li>
 *   <li>{@link #onParseError(Exception, boolean)} — 解析失败或恢复失败时调用</li>
 *   <li>{@link #onParseInterrupted()} — 线程被中断时调用（默认空实现）</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 回调方法通常由解码器的工作线程直接调用，因此实现类应确保回调方法是线程安全的，
 * 且避免执行耗时操作以免阻塞解析过程。如需跨线程传递数据，应使用线程安全的数据结构。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Decoder<BufferedImage> decoder = new GifDecoder();
 * decoder.setParseCallback(new ParseCallback() {
 *     @Override
 *     public void onHeader(Header header) {
 *         System.out.println("GIF 尺寸: " + header.width() + "x" + header.height());
 *     }
 *
 *     @Override
 *     public void onFrameParsed(FrameInfo frameInfo) {
 *         System.out.println("帧 " + frameInfo.getIndex() + " 延迟: " + frameInfo.getDelayTime() + "ms");
 *     }
 *
 *     @Override
 *     public void onParseError(Exception error, boolean recovered) {
 *         System.err.println("解析错误: " + error.getMessage());
 *     }
 *
 *     // 其他方法按需实现...
 * });
 * decoder.load("animated.gif");
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see Header
 * @see FrameInfo
 * @see Extension
 */
public interface ParseCallback {

    /**
     * 当 GIF 头部信息（Header）解析成功时调用。
     * <p>
     * 头部信息包含逻辑屏幕宽度、高度、全局调色板标志、颜色分辨率等元数据。
     * 此回调通常在解析流程的最开始触发，且仅调用一次。
     * </p>
     *
     * @param header 解析出的头部信息对象，包含屏幕尺寸、颜色深度等属性，不能为 {@code null}
     */
    void onHeader(Header header);

    /**
     * 当解析到一个全局扩展块时调用。
     * <p>
     * 全局扩展块通常出现在图像数据之前，用于提供全局注释、应用标识或图形控制参数。
     * 每遇到一个扩展块即回调一次，可能被调用零次或多次。
     * </p>
     *
     * @param extension 解析出的扩展块对象，具体类型可通过 {@code instanceof} 判断，
     *                  不能为 {@code null}
     */
    void onGlobalExtensionParsed(Extension extension);

    /**
     * 当一帧的元数据解析完成时调用。
     * <p>
     * 帧元数据包括帧在 GIF 中的索引、延迟时间、透明色索引、局部调色板信息等。
     * 此回调在每帧的图像数据解码之前触发，便于调用者提前获取帧属性。
     * </p>
     *
     * @param frameInfo 帧元数据信息对象，包含索引、延迟、尺寸等属性，不能为 {@code null}
     */
    void onFrameParsed(FrameInfo frameInfo);

    /**
     * 当 GIF 解析全部完成时调用。
     * <p>
     * 表示所有数据块（头部、扩展块、帧数据）均已成功解析，无错误发生。
     * 此回调在正常流程结束时触发，且仅调用一次。
     * </p>
     */
    void onParseComplete();

    /**
     * 当解析过程中发生错误且无法恢复时调用。
     * <p>
     * 错误可能发生在任何阶段，如数据格式损坏、不支持的编码格式等。
     * 调用者可根据 {@code recovered} 参数判断是否曾经尝试过恢复但最终失败。
     * </p>
     *
     * @param error     发生的异常对象，不能为 {@code null}
     * @param recovered 是否已尝试过恢复：{@code true} 表示尝试恢复但失败，{@code false} 表示直接失败未尝试恢复
     */
    void onParseError(Exception error, boolean recovered);

    /**
     * 当解析器尝试从错误中恢复时调用。
     * <p>
     * 默认实现为空，子类可覆盖以监控恢复进度或记录日志。
     * 当解析器遇到可恢复的错误（如非关键数据块损坏）时，会尝试跳过该块并继续解析，
     * 每次尝试会回调此方法。
     * </p>
     *
     * @param recoveryCount       当前恢复尝试次数（从 1 开始计数）
     * @param maxRecoveryAttempts 最大允许的恢复尝试次数
     * @param error               导致本次恢复的异常对象，不能为 {@code null}
     */
    default void onRecoveryAttempt(int recoveryCount, int maxRecoveryAttempts, Exception error) {}

    /**
     * 当解析线程被中断时调用。
     * <p>
     * 默认实现为空，子类可覆盖以执行中断清理操作（如设置中断标志、释放资源等）。
     * 注意：此方法被调用时，解析过程通常已停止，调用者不应再依赖后续回调。
     * </p>
     */
    default void onParseInterrupted() {}
}