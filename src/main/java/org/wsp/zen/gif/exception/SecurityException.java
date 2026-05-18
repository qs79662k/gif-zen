package org.wsp.zen.gif.exception;

/**
 * GIF 安全异常，表示解析或处理过程中触发了安全限制。
 * <p>
 * 该异常继承自 {@link RuntimeException}，用于标识因超出预设安全阈值而导致的错误。
 * 常见的触发场景包括：文件大小超过允许上限、图像尺寸超出限制、解码内存占用过高等。
 * 抛出此异常时，解码或解析流程应立即终止，以防止资源耗尽或拒绝服务攻击。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Decoder<BufferedImage> decoder = new GifDecoder();
 * decoder.setMaxFileSize(10 * 1024 * 1024); // 10 MB
 * try {
 *     decoder.load("large.gif");
 * } catch (SecurityException e) {
 *     System.err.println("安全限制触发: " + e.getMessage());
 *     // 提示用户文件过大或不符合安全策略
 * }
 * }</pre>
 *
 * <p><b>与其他异常的关系：</b>
 * 此异常与 {@link DecoderException} 同为运行时异常，但侧重于安全策略违反而非数据格式问题。
 * 调用方可通过分别捕获这两类异常来区分安全限制与解码错误。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see RuntimeException
 * @see DecoderException
 */
public class SecurityException extends RuntimeException {

    /** 序列化版本 ID，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用指定的最大允许大小构造一个新的安全异常。
     * <p>
     * 异常消息将自动格式化为：{@code "文件超过安全限制: " + maxSize + " bytes"}。
     * </p>
     *
     * @param maxSize 允许的最大字节数，用于在异常消息中展示限制值
     */
    public SecurityException(long maxSize) {
        super("文件超过安全限制: " + maxSize + " bytes");
    }
}