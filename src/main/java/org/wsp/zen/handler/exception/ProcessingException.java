package org.wsp.zen.handler.exception;

/**
 * 处理器执行异常，表示在数据读取、解码或渲染等处理阶段发生的运行时错误。
 * <p>
 * 该异常继承自 {@link RuntimeException}，用于包装在处理器流水线中出现的各类异常，
 * 例如 I/O 错误、数据格式错误、渲染失败等。调用方可通过捕获此异常来获知处理失败的具体原因，
 * 并进行相应的清理或降级处理。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Handler<FrameData> handler = ...;
 * try {
 *     handler.process(context);
 * } catch (ProcessingException e) {
 *     System.err.println("处理失败: " + e.getMessage());
 *     // 执行清理或回退操作
 * }
 * }</pre>
 *
 * <p><b>与其他异常的关系：</b>
 * 此异常为处理器模块的统一运行时异常，通常用于包装底层的 {@link java.io.IOException}、
 * {@link org.wsp.zen.gif.exception.DecoderException} 等受检异常或运行时异常，
 * 避免在处理器接口中声明过多的异常类型。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see RuntimeException
 * @see org.wsp.zen.handler.core.Handler
 */
public class ProcessingException extends RuntimeException {

    /** 序列化版本 ID，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用指定的错误消息构造一个新的处理器异常。
     *
     * @param message 详细的错误描述信息，不能为 {@code null}
     */
    public ProcessingException(String message) {
        super(message);
    }

    /**
     * 使用指定的错误消息和根本原因构造一个新的处理器异常。
     *
     * @param message 详细的错误描述信息，不能为 {@code null}
     * @param cause   导致此异常的根本原因，可以为 {@code null}
     */
    public ProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}