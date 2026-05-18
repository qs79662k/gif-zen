package org.wsp.zen.gif.exception;

/**
 * LZW 无效码字异常，表示在 LZW 解压缩过程中遇到了超出有效范围的码字。
 * <p>
 * 该异常继承自 {@link IllegalArgumentException}，用于明确标识输入数据中出现的
 * LZW 码字违反了 GIF 规范或当前解码上下文的状态（例如码字值大于当前码表最大索引、
 * 在码表尚未构建完成时引用了未定义的码字等）。抛出此异常通常意味着数据流已损坏，
 * 或者解码器实现与编码器不兼容。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * try {
 *     // 执行 LZW 解码，从流中读取码字
 *     int code = readNextCode();
 *     if (code < 0 || code >= codeTable.size()) {
 *         throw new LzwInvalidCodeException("无效码字: " + code + "，当前码表大小: " + codeTable.size());
 *     }
 * } catch (LzwInvalidCodeException e) {
 *     // 记录错误并尝试恢复或终止解码
 *     System.err.println("LZW 无效码字: " + e.getMessage());
 * }
 * }</pre>
 *
 * <p><b>与其他 LZW 异常的关系：</b>
 * 此异常与 {@link LzwCorruptedDataException} 和 {@link LzwDecodingException}
 * 共同构成 LZW 解码过程的异常体系。其中：
 * <ul>
 *   <li>{@code LzwCorruptedDataException} 是通用的数据损坏异常基类</li>
 *   <li>{@code LzwDecodingException} 表示解码过程中的一般性错误</li>
 *   <li>{@code LzwInvalidCodeException} 专门用于指示具体的码字值非法</li>
 * </ul>
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see IllegalArgumentException
 * @see LzwCorruptedDataException
 * @see LzwDecodingException
 * @see org.wsp.zen.gif.core.LzwDecompressor
 */
public class LzwInvalidCodeException extends IllegalArgumentException {

    /** 序列化版本 ID，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用指定的错误消息构造一个新的 LZW 无效码字异常。
     *
     * @param message 详细的错误描述信息，说明码字为何无效，不能为 {@code null}
     */
    public LzwInvalidCodeException(String message) {
        super(message);
    }

    /**
     * 使用指定的根本原因构造一个新的 LZW 无效码字异常。
     * <p>
     * 错误消息将使用 {@code cause.toString()} 的值，通常用于包装底层异常。
     * </p>
     *
     * @param cause 导致此异常的根本原因，不能为 {@code null}
     */
    public LzwInvalidCodeException(Throwable cause) {
        super(cause);
    }

    /**
     * 使用指定的错误消息和根本原因构造一个新的 LZW 无效码字异常。
     *
     * @param message 详细的错误描述信息，不能为 {@code null}
     * @param cause   导致此异常的根本原因，可以为 {@code null}
     */
    public LzwInvalidCodeException(String message, Throwable cause) {
        super(message, cause);
    }
}