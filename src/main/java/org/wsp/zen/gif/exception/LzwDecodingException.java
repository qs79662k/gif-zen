package org.wsp.zen.gif.exception;

/**
 * LZW 解码异常，表示在 LZW 解压缩过程中发生的特定解码错误。
 * <p>
 * 该异常继承自 {@link LzwCorruptedDataException}，用于更精确地标识解码阶段的错误，
 * 例如码字超出当前码表范围、无效的清除码或结束码位置异常等。与父类的区别在于，
 * 它强调解码逻辑本身检测到的错误，而非数据流的一般性损坏。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * try {
 *     // 执行 LZW 解码操作
 * } catch (LzwDecodingException e) {
 *     System.err.println("LZW 解码失败: " + e.getMessage());
 *     // 可根据具体错误决定恢复策略
 * }
 * }</pre>
 *
 * <p><b>继承关系：</b>
 * 作为 {@code LzwCorruptedDataException} 的子类，该异常同样继承自
 * {@link java.util.zip.DataFormatException}，表明数据格式层面的问题。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see LzwCorruptedDataException
 * @see org.wsp.zen.gif.core.LzwDecompressor
 */
public class LzwDecodingException extends LzwCorruptedDataException {

    /** 序列化版本 ID，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用指定的错误消息构造一个新的 LZW 解码异常。
     *
     * @param message 详细的错误描述信息，说明解码过程中遇到的具体问题，不能为 {@code null}
     */
    public LzwDecodingException(String message) {
        super(message);
    }
}