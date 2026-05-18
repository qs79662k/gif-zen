package org.wsp.zen.gif.exception;

/**
 * GIF 解码器运行时异常，用于包装解析、I/O、初始化等过程中的不可恢复错误。
 * <p>
 * 在解码器的整个生命周期中，任何无法通过容错机制恢复的严重错误（如文件格式损坏、
 * 不支持的编码方式、内存分配失败等）都会导致抛出此异常或其子类。
 * 调用方可通过捕获此异常来获知解码失败的具体原因，并进行相应的清理或降级处理。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Decoder<BufferedImage> decoder = new GifDecoder();
 * try {
 *     decoder.load("corrupted.gif");
 *     // 后续解码操作...
 * } catch (DecoderException e) {
 *     System.err.println("GIF 解码失败: " + e.getMessage());
 *     // 执行清理或使用默认图像
 * }
 * }</pre>
 *
 * <p><b>继承关系：</b>
 * 该异常继承自 {@link RuntimeException}，属于未检查异常。设计为运行时异常是为了
 * 避免在每次调用解码相关方法时强制要求 {@code try-catch}，同时允许调用方在
 * 适当层级统一处理解码错误。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see RuntimeException
 */
public class DecoderException extends RuntimeException {

    /** 序列化版本 ID，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用指定的错误消息构造一个新的解码器异常。
     *
     * @param message 详细的错误描述信息，不能为 {@code null}
     */
    public DecoderException(String message) {
        super(message);
    }

    /**
     * 使用指定的错误消息和根本原因构造一个新的解码器异常。
     *
     * @param message 详细的错误描述信息，不能为 {@code null}
     * @param cause   导致此异常的根本原因，可以为 {@code null}
     */
    public DecoderException(String message, Throwable cause) {
        super(message, cause);
    }
}