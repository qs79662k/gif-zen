package org.wsp.zen.gif.exception;

/**
 * LZW 零字节长度异常，表示在 LZW 解压缩过程中遇到了长度为零的数据块。
 * <p>
 * 根据 GIF 规范，LZW 压缩数据以子块（Sub-block）形式存储，每个子块以单字节长度开头，
 * 后跟相应长度的数据。长度字节为零表示数据块结束。然而，在数据块内部或某些特定上下文中，
 * 出现零字节长度可能表示数据截断、编码错误或文件损坏。
 * 此异常用于明确标识这种非预期的零长度情况。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * try {
 *     int blockSize = inputStream.read();
 *     if (blockSize == 0) {
 *         // 根据上下文判断是否为正常结束符
 *         if (expectMoreData) {
 *             throw new LzwZeroByteLengthException("预期数据块，但读取到零长度块");
 *         }
 *         // 正常结束处理...
 *     }
 * } catch (LzwZeroByteLengthException e) {
 *     System.err.println("LZW 数据块异常: " + e.getMessage());
 * }
 * }</pre>
 *
 * <p><b>与其他 LZW 异常的关系：</b>
 * 此异常与 {@link LzwCorruptedDataException}、{@link LzwDecodingException}
 * 和 {@link LzwInvalidCodeException} 共同构成 LZW 解码过程的异常体系。
 * 其中：
 * <ul>
 *   <li>{@code LzwCorruptedDataException} 是通用的数据损坏异常基类</li>
 *   <li>{@code LzwDecodingException} 表示解码过程中的一般性错误</li>
 *   <li>{@code LzwInvalidCodeException} 指示码字值非法</li>
 *   <li>{@code LzwZeroByteLengthException} 专门用于数据块长度为零的异常情况</li>
 * </ul>
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see IllegalArgumentException
 * @see LzwCorruptedDataException
 * @see LzwDecodingException
 * @see LzwInvalidCodeException
 * @see org.wsp.zen.gif.core.LzwDecompressor
 */
public class LzwZeroByteLengthException extends IllegalArgumentException {

    /** 序列化版本 ID，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用指定的错误消息构造一个新的 LZW 零字节长度异常。
     *
     * @param message 详细的错误描述信息，说明为何出现零长度数据块，不能为 {@code null}
     */
    public LzwZeroByteLengthException(String message) {
        super(message);
    }

    /**
     * 使用指定的根本原因构造一个新的 LZW 零字节长度异常。
     * <p>
     * 错误消息将使用 {@code cause.toString()} 的值，通常用于包装底层异常。
     * </p>
     *
     * @param cause 导致此异常的根本原因，不能为 {@code null}
     */
    public LzwZeroByteLengthException(Throwable cause) {
        super(cause);
    }

    /**
     * 使用指定的错误消息和根本原因构造一个新的 LZW 零字节长度异常。
     *
     * @param message 详细的错误描述信息，不能为 {@code null}
     * @param cause   导致此异常的根本原因，可以为 {@code null}
     */
    public LzwZeroByteLengthException(String message, Throwable cause) {
        super(message, cause);
    }
}