package org.wsp.zen.gif.exception;

import java.io.IOException;

/**
 * GIF 解析异常，表示在解析 GIF 文件格式过程中发生的错误。
 * <p>
 * 该异常继承自 {@link IOException}，用于标识与 GIF 数据流解析相关的可恢复或不可恢复错误。
 * 与 {@link DecoderException}（运行时异常）不同，此异常为受检异常，强制调用方显式处理
 * 解析阶段的 I/O 或格式问题。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Parser parser = new MyGifParser();
 * try (InputStream in = Files.newInputStream(Paths.get("image.gif"))) {
 *     parser.parseAsync(in, callback).get();
 * } catch (ParseException e) {
 *     System.err.println("GIF 解析失败: " + e.getMessage());
 *     // 执行错误恢复或使用默认图像
 * } catch (IOException e) {
 *     System.err.println("读取文件时发生 I/O 错误: " + e.getMessage());
 * }
 * }</pre>
 *
 * <p><b>与其他异常的关系：</b>
 * <ul>
 *   <li>{@link DecoderException} — 解码器整体运行时异常，通常包装更底层的错误</li>
 *   <li>{@link LzwCorruptedDataException} — LZW 数据损坏，继承自 {@link java.util.zip.DataFormatException}</li>
 *   <li>{@code ParseException} — 解析阶段的 I/O 相关异常，继承自 {@link IOException}</li>
 * </ul>
 *
 * @author wsp
 * @version 1.0
 * @see java.io.IOException
 * @see org.wsp.zen.gif.core.Parser
 * @see DecoderException
 */
public class ParseException extends IOException {

    /** 序列化版本 ID，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用指定的错误消息构造一个新的解析异常。
     *
     * @param message 详细的错误描述信息，不能为 {@code null}
     */
    public ParseException(String message) {
        super(message);
    }

    /**
     * 使用指定的错误消息和根本原因构造一个新的解析异常。
     *
     * @param message 详细的错误描述信息，不能为 {@code null}
     * @param cause   导致此异常的根本原因，可以为 {@code null}
     */
    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}