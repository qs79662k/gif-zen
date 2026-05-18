package org.wsp.zen.gif.core;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.wsp.zen.gif.extension.ApplicationExtension;
import org.wsp.zen.gif.extension.CommentExtension;
import org.wsp.zen.gif.extension.PlainTextExtension;

/**
 * GIF 解码器核心接口，支持异步获取帧图像、元数据查询及资源管理。
 * <p>
 * 典型使用流程：
 * <ol>
 *   <li>创建解码器实例（通过工厂或直接构造）</li>
 *   <li>调用 {@link #load(InputStream)} 或 {@link #load(String)} 加载 GIF 数据</li>
 *   <li>等待 {@link #isLoadComplete()} 返回 {@code true} 后，查询帧总数、循环次数等信息</li>
 *   <li>通过 {@link #getFrame(int)} 异步获取帧图像，并通过 {@link #getDelayTime(int)} 获取帧延迟</li>
 *   <li>使用完毕后调用 {@link #close()} 释放资源（推荐使用 try-with-resources）</li>
 * </ol>
 *
 * <p><b>线程安全性：</b>
 * 实现类不要求全局线程安全。通常，{@code load}、{@code reset}、{@code close} 应在同一线程调用，
 * 而 {@code getFrame} 返回的 {@link CompletableFuture} 可安全地在任意线程中等待和消费。
 * 除非实现类明确声明，否则不应从多个线程并发调用同一实例的非异步方法。
 *
 * <p><b>资源管理：</b>
 * 实现 {@link AutoCloseable}，{@link #close()} 应释放所有底层资源（输入流、内存缓冲区、解码器状态）。
 * 调用 {@code close()} 后，除 {@code close()} 本身外，任何其他方法都应抛出 {@link IllegalStateException}。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * Decoder<BufferedImage> decoder = new GifDecoder();
 * try (InputStream in = Files.newInputStream(Paths.get("animated.gif"))) {
 *     decoder.load(in);
 *     while (!decoder.isLoadComplete()) {
 *         Thread.sleep(10); // 等待解析完成
 *     }
 *
 *     int frameCount = decoder.getFrameCount();
 *     for (int i = 0; i < frameCount; i++) {
 *         BufferedImage frame = decoder.getFrame(i).get();
 *         int delay = decoder.getDelayTime(i);
 *         // 处理帧图像与延迟...
 *     }
 * } finally {
 *     decoder.close();
 * }
 * }</pre>
 *
 * @param <T> 帧图像的类型，通常为 {@link java.awt.image.BufferedImage} 或 {@link javafx.scene.image.Image}
 *            具体类型由实现类决定
 * @author wsp
 * @version 1.0
 * @see java.awt.image.BufferedImage
 */
public interface Decoder<T> extends AutoCloseable {

    /**
     * 从输入流加载 GIF 数据。
     * <p>
     * 调用此方法会重置解码器状态（相当于先调用 {@link #reset()}），然后开始解析输入流。
     * 解析过程可能异步执行，可以通过 {@link #isLoadComplete()} 查询是否完成。
     * </p>
     *
     * @param inputStream GIF 数据的输入流，不能为 {@code null}。调用者负责流的生命周期，实现类不应关闭该流。
     * @throws NullPointerException     如果 {@code inputStream} 为 {@code null}
     * @throws IllegalStateException    如果解码器已经关闭
     * @throws IllegalArgumentException 如果输入流内容不是有效的 GIF 格式（具体异常可能延迟到解析时抛出）
     */
    void load(InputStream inputStream);

    /**
     * 从文件路径或 URL 加载 GIF 数据。
     * <p>
     * 语义同 {@link #load(InputStream)}，实现类负责打开并读取指定位置的资源。
     * </p>
     *
     * @param source GIF 资源的路径（绝对或相对）、文件路径或 URL 字符串，不能为 {@code null} 或空
     * @throws NullPointerException     如果 {@code source} 为 {@code null}
     * @throws IllegalArgumentException 如果 {@code source} 为空字符串或格式不支持
     * @throws IllegalStateException    如果解码器已经关闭
     * @throws RuntimeException         如果无法打开资源或解析失败（具体异常由实现包装）
     */
    void load(String source);

    /**
     * 返回 GIF 的总帧数。
     * <p>
     * 只有在 {@link #isLoadComplete()} 返回 {@code true} 后才能获得有效值。
     * 未加载或加载未完成时，实现可能返回 0 或抛出异常。
     * </p>
     *
     * @return 总帧数，始终 ≥ 0
     * @throws IllegalStateException 如果解码器未加载数据、加载未完成或已关闭
     */
    int getFrameCount();

    /**
     * 异步获取指定索引的帧图像。
     * <p>
     * 返回的 {@link CompletableFuture} 会在帧解码完成后完成（正常结束或异常结束）。
     * 解码可能涉及 I/O 或计算，建议实现类在独立线程池中执行，避免阻塞调用线程。
     * 多次调用同一索引可能返回不同的 Future 实例，但应解码得到相同的图像内容。
     * </p>
     *
     * @param index 帧索引，从 0 开始，必须小于 {@link #getFrameCount()}
     * @return 包含帧图像的 {@code CompletableFuture}；如果解码成功，则 {@link CompletableFuture#get()} 返回图像对象；
     * 如果失败，则 Future 以相应异常完成（如 {@link java.io.IOException}、{@link IndexOutOfBoundsException}）
     * @throws IllegalArgumentException 如果索引超出有效范围（实现也可能在 Future 中报告此异常）
     * @throws IllegalStateException    如果解码器未加载数据、加载未完成或已关闭
     */
    CompletableFuture<T> getFrame(int index);

    /**
     * 获取指定帧的延迟时间（单位：毫秒）。
     * <p>
     * 注意：GIF 标准中延迟时间以 10 毫秒为单位存储，此方法应返回转换后的毫秒值。
     * 如果帧未定义延迟（某些 GIF 生成器可能省略），应返回 0 或默认值（如 100 毫秒），具体由实现决定。
     * </p>
     *
     * @param index 帧索引，从 0 开始，必须小于 {@link #getFrameCount()}
     * @return 延迟时间，单位毫秒，≥ 0
     * @throws IllegalArgumentException 如果索引超出有效范围
     * @throws IllegalStateException    如果解码器未加载数据、加载未完成或已关闭
     */
    int getDelayTime(int index);

    /**
     * 检查是否已完成 GIF 的解析。
     * <p>
     * 完成意味着所有帧的元数据（帧数、延迟时间、循环次数、扩展块等）已解析完毕，
     * 但帧图像本身可能尚未解码（解码在 {@link #getFrame} 时按需进行）。
     * </p>
     *
     * @return {@code true} 表示加载解析已完成，可以安全调用 {@link #getFrameCount()}、
     * {@link #getDelayTime(int)} 等方法；否则返回 {@code false}
     */
    boolean isLoadComplete();

    /**
     * 返回动画的循环次数。
     * <p>
     * 值含义：
     * <ul>
     *   <li>0 表示无限循环</li>
     *   <li>正整数表示循环次数（例如 1 表示只播放一次，即不重复）</li>
     * </ul>
     * 如果 GIF 没有显式定义循环次数（如缺少 Netscape 应用扩展），通常返回 0（无限循环）或 1（只播放一次），
     * 具体由实现决定。
     * </p>
     *
     * @return 循环次数，≥ 0
     * @throws IllegalStateException 如果解码器未加载数据、加载未完成或已关闭
     */
    int getLoopCount();

    /**
     * 返回 GIF 的应用扩展块（Application Extension）。
     * <p>
     * 最常见的应用扩展是 Netscape 扩展（用于指定循环次数）。
     * 如果 GIF 中没有应用扩展块，则返回 {@code null}。
     * </p>
     *
     * @return 应用扩展块对象，可能为 {@code null}
     * @throws IllegalStateException 如果解码器未加载数据、加载未完成或已关闭
     */
    ApplicationExtension getApplicationExtension();

    /**
     * 返回所有注释扩展块（Comment Extension）的不可修改列表。
     * <p>
     * 注释扩展用于存储文本注释，允许多个。如果没有注释扩展，返回空列表（非 {@code null}）。
     * </p>
     *
     * @return 注释扩展列表（不可变视图），永远不为 {@code null}
     * @throws IllegalStateException 如果解码器未加载数据、加载未完成或已关闭
     */
    List<CommentExtension> getCommentExtensions();

    /**
     * 返回所有纯文本扩展块（Plain Text Extension）的不可修改列表。
     * <p>
     * 纯文本扩展用于嵌入可渲染的文本。如果没有此类扩展，返回空列表。
     * </p>
     *
     * @return 纯文本扩展列表（不可变视图），永远不为 {@code null}
     * @throws IllegalStateException 如果解码器未加载数据、加载未完成或已关闭
     */
    List<PlainTextExtension> getPlainTextExtensions();

    /**
     * 重置解码器状态，释放所有已解析的数据，恢复到未加载状态。
     * <p>
     * 重置后，可以重新调用 {@link #load} 加载新的 GIF 数据。
     * 此操作不会关闭解码器（即不会释放底层资源，如流等由调用者管理的资源）。
     * 调用 {@code reset()} 后，之前通过 {@link #getFrame} 返回的未完成的 {@link CompletableFuture}
     * 可能被取消或以异常完成，具体行为由实现决定。
     * </p>
     *
     * @throws IllegalStateException 如果解码器已关闭
     */
    void reset();

    /**
     * 关闭解码器并释放所有底层资源（如文件句柄、临时缓冲区、解码线程池等）。
     * <p>
     * 调用后，除本方法外的任何方法都应抛出 {@link IllegalStateException}。
     * 重复调用 {@code close()} 没有额外效果。
     * 推荐使用 try-with-resources 语句管理解码器实例。
     * </p>
     *
     * @throws Exception 如果释放资源时发生错误（通常不抛出，但为满足 {@link AutoCloseable} 签名而声明）
     */
    @Override
    void close() throws Exception;
}