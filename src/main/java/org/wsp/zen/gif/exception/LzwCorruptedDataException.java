package org.wsp.zen.gif.exception;

import java.util.zip.DataFormatException;

/**
 * LZW 数据损坏异常，用于表示 GIF 压缩图像数据在解压过程中遇到的格式错误。
 * <p>
 * 当 LZW 解压器在解码压缩的图像索引流时，若检测到违反 LZW 规范的错误（如码字溢出、
 * 码表构建异常、数据意外结束等），将抛出此异常。该异常继承自 {@link DataFormatException}，
 * 明确表示输入数据格式不符合预期，而非 I/O 或系统级错误。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * LzwDecompressor decompressor = new MyLzwDecompressor();
 * try {
 *     byte[] indices = decompressor.decodeFrame(compressedData, false, 8, 320, 240);
 *     // 使用解码后的索引数据...
 * } catch (LzwCorruptedDataException e) {
 *     System.err.println("LZW 数据损坏，无法解码帧: " + e.getMessage());
 *     // 可选择丢弃当前帧或尝试恢复
 * }
 * }</pre>
 *
 * <p><b>与 {@link DecoderException} 的区别：</b>
 * 此异常专门用于 LZW 数据解压层面的错误，而 {@code DecoderException} 是解码器整体
 * 层面的运行时异常。在 GIF 解析过程中，LZW 错误可能被捕获并包装为 {@code DecoderException}，
 * 也可能直接向上抛出，具体取决于实现设计。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see java.util.zip.DataFormatException
 * @see org.wsp.zen.gif.core.LzwDecompressor
 * @see DecoderException
 */
public class LzwCorruptedDataException extends DataFormatException {

    /** 序列化版本 ID，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用指定的错误消息构造一个新的 LZW 数据损坏异常。
     *
     * @param message 详细的错误描述信息，说明数据为何不符合 LZW 规范，不能为 {@code null}
     */
    public LzwCorruptedDataException(String message) {
        super(message);
    }
}