package org.wsp.zen.gif.exception;

import java.io.IOException;

/**
 * GIF 解析恢复异常，表示在尝试从解析错误中恢复时发生的失败。
 * <p>
 * 当解析器遇到数据损坏并尝试通过跳过损坏部分、定位下一个有效数据块等方式恢复解析时，
 * 若恢复过程本身失败（例如无法找到有效数据块起始标识、恢复次数超过上限等），
 * 将抛出此异常。该异常继承自 {@link IOException}，属于受检异常，表明解析流程
 * 因无法恢复而必须终止。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Parser parser = new MyGifParser();
 * try (InputStream in = Files.newInputStream(Paths.get("damaged.gif"))) {
 *     parser.parseAsync(in, callback).get();
 * } catch (ParseRecoveryException e) {
 *     System.err.println("GIF 解析恢复失败，无法继续: " + e.getMessage());
 *     // 终止解析，提示用户文件已损坏
 * } catch (ParseException e) {
 *     System.err.println("GIF 解析错误: " + e.getMessage());
 * } catch (IOException e) {
 *     System.err.println("I/O 错误: " + e.getMessage());
 * }
 * }</pre>
 *
 * <p><b>与其他异常的关系：</b>
 * <ul>
 *   <li>{@link ParseException} — 一般的解析错误，可能包含可恢复和不可恢复两种情况</li>
 *   <li>{@code ParseRecoveryException} — 专用于恢复失败，表示解析器已尽力恢复但仍无法继续</li>
 *   <li>{@link DecoderException} — 解码器整体层面的运行时异常</li>
 * </ul>
 *
 * @author wsp
 * @version 1.0
 * @see java.io.IOException
 * @see org.wsp.zen.gif.core.Parser
 * @see ParseException
 * @see org.wsp.zen.gif.core.RecoveryCallback
 */
public class ParseRecoveryException extends IOException {

    /** 序列化版本 ID，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用指定的错误消息构造一个新的解析恢复异常。
     *
     * @param message 详细的错误描述信息，说明恢复为何失败，不能为 {@code null}
     */
    public ParseRecoveryException(String message) {
        super(message);
    }

    /**
     * 使用指定的错误消息和根本原因构造一个新的解析恢复异常。
     *
     * @param message 详细的错误描述信息，不能为 {@code null}
     * @param cause   导致此异常的根本原因，可以为 {@code null}
     */
    public ParseRecoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}