package org.wsp.zen.gif.core;

import java.io.Closeable;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * GIF 解析器接口，支持异步解析 GIF 数据流并通过回调通知解析事件。
 * <p>
 * 实现类负责解析 GIF 文件格式的二进制数据，包括文件头、逻辑屏幕描述块、全局调色板、
 * 扩展块以及图像数据块。解析过程中遇到的关键信息（如图像帧、延迟时间、注释等）会通过
 * {@link ParseCallback} 回调通知调用方。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * // 准备对象池管理器（已注册所有必需的对象池）和 LZW 解压器
 * PoolManager poolManager = ...;
 * LzwDecompressor decompressor = ...;
 *
 * // 通过 Builder 构造解析器，同时注入必需依赖
 * Parser parser = new DefaultParser.Builder()
 *         .withLzwDecompressor(decompressor)
 *         .withPoolManager(poolManager)
 *         .build();
 *
 * InputStream in = Files.newInputStream(Paths.get("animated.gif"));
 * CompletableFuture<Void> future = parser.parseAsync(in, new ParseCallback() {
 *     @Override
 *     public void onHeader(Header header) {
 *         System.out.println("宽度: " + header.width());
 *     }
 *
 *     @Override
 *     public void onFrameParsed(FrameInfo frameInfo) {
 *         System.out.println("帧延迟: " + frameInfo.getDelayTime() + "ms");
 *     }
 *
 *     @Override
 *     public void onParseComplete() {
 *         System.out.println("解析完成");
 *     }
 *
 *     @Override
 *     public void onParseError(Exception error, boolean recovered) {
 *         System.err.println("解析失败: " + error.getMessage());
 *     }
 * });
 *
 * future.thenRun(() -> {
 *     try {
 *         in.close();
 *         parser.close();
 *     } catch (IOException e) {
 *         e.printStackTrace();
 *     }
 * }).exceptionally(ex -> {
 *     System.err.println("解析异常: " + ex);
 *     return null;
 * });
 * }</pre>
 *
 * <p><b>依赖注入：</b>
 * 解析器所需的 LZW 解压器和对象池管理器应在构造时通过 Builder 提供，且对象池管理器中
 * 必须已注册必需的字节数组池。具体实现类会在构造时立即校验这些依赖，若未提供或缺失
 * 必需的资源池则抛出异常。这种设计保证了线程安全且消除了延迟注入导致的不确定性。
 * </p>
 *
 * <p><b>资源管理：</b>
 * 继承自 {@link Closeable}，调用方在使用完毕后应调用 {@link #close()} 释放解析器占用的资源
 * （如内部线程池、文件句柄等）。通常在 {@link CompletableFuture} 完成后关闭，或使用
 * try-with-resources 语句管理解析器生命周期。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 不建议对同一个解析器实例同时调用多次 {@code parseAsync}。
 * 如果重复调用，实现类应抛出 {@link IllegalStateException} 或等待前一个解析完成（具体行为由实现决定）。
 * 回调方法通常在解析线程中执行，实现类应确保回调不会长时间阻塞。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see ParseCallback
 * @see LzwDecompressor
 * @see CompletableFuture
 */
public interface Parser extends Closeable {

    /**
     * 异步解析 GIF 输入流，解析结果通过回调通知。
     * <p>
     * 调用此方法后立即返回一个 {@link CompletableFuture}，实际解析在另一个线程中执行。
     * 解析过程中每当识别出特定的 GIF 数据块（头部、图像帧、扩展块等）时，会调用
     * {@link ParseCallback} 中对应的方法。解析完成（无论成功或失败）时，
     * 返回的 {@code CompletableFuture} 会完成：
     * <ul>
     *   <li>正常完成（{@code CompletableFuture.complete(null)}）表示解析成功结束，所有数据已处理。</li>
     *   <li>异常完成（{@code CompletableFuture.completeExceptionally(...)}）表示解析过程中发生错误，
     *       例如格式错误、I/O 异常或数据损坏。</li>
     * </ul>
     *
     * <p><b>关于输入流：</b>
     * 传入的 {@link InputStream} 由调用方负责关闭。解析器不会在解析结束后自动关闭该流，
     * 但可能会在解析过程中读取流的内容。调用方应等待 {@code CompletableFuture} 完成后
     * 再关闭流（或使用 try-with-resources 管理流和解析器的生命周期）。
     * </p>
     *
     * @param in       GIF 数据的输入流，不能为 {@code null}。解析器会从流的当前位置开始读取，
     *                 不会自动重置或标记。
     * @param callback 解析回调，用于接收解析过程中遇到的各种 GIF 结构，不能为 {@code null}。
     *                 回调方法会在解析线程中被调用，实现类应确保回调不会阻塞过长时间，
     *                 否则会影响解析进度。
     * @return 一个 {@code CompletableFuture}，用于等待解析完成或获取异常。
     *         返回的 Future 不支持取消（取消操作可能无效或引发异常），具体行为由实现决定。
     * @throws NullPointerException 如果 {@code in} 或 {@code callback} 为 {@code null}
     * @throws IllegalStateException 如果解析器已经关闭，或已有未完成的解析任务（取决于实现）
     */
    CompletableFuture<Void> parseAsync(InputStream in, ParseCallback callback);
}