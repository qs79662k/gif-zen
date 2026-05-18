package org.wsp.zen.io.util;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * IO 异常转换工具类，将受检的 {@link IOException} 包装为 {@link UncheckedIOException}。
 * <p>
 * 适用于在 lambda 表达式或流式操作中需要抛出受检异常的场景，允许在不修改接口签名的情况下
 * 抛出未检查异常，简化错误处理代码。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>
 * // 包装有返回值的操作
 * byte[] data = IOExceptionUtil.wrapSupplier(() -> {
 *     return Files.readAllBytes(Paths.get("file.txt"));
 * });
 *
 * // 包装无返回值的操作
 * IOExceptionUtil.wrapAction(() -> {
 *     Files.deleteIfExists(Paths.get("temp.tmp"));
 * });
 * </pre>
 *
 * @author wsp
 * @version 1.0
 * @see UncheckedIOException
 */
public final class IOExceptionUtil {

    private IOExceptionUtil() {
        // 私有构造器，防止实例化
    }

    /**
     * 执行一个可能抛出 {@link IOException} 的供应操作，并将受检异常包装为 {@link UncheckedIOException}。
     *
     * @param supplier 供应操作，不能为 {@code null}
     * @param <T>      返回值的类型
     * @return 供应操作返回的结果
     * @throws NullPointerException  如果 {@code supplier} 为 {@code null}
     * @throws UncheckedIOException 如果 {@code supplier.execute()} 抛出 {@link IOException}
     */
    public static <T> T wrapSupplier(SupplierWithIO<T> supplier) {
        try {
            return supplier.execute();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 执行一个可能抛出 {@link IOException} 的动作操作，并将受检异常包装为 {@link UncheckedIOException}。
     *
     * @param action 动作操作，不能为 {@code null}
     * @throws NullPointerException  如果 {@code action} 为 {@code null}
     * @throws UncheckedIOException 如果 {@code action.execute()} 抛出 {@link IOException}
     */
    public static void wrapAction(ActionWithIO action) {
        try {
            action.execute();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 功能性接口，表示一个可能抛出 {@link IOException} 的供应操作（有返回值）。
     *
     * @param <T> 返回值的类型
     */
    public interface SupplierWithIO<T> {
        /**
         * 执行可能抛出 IO 异常的操作。
         *
         * @return 操作结果
         * @throws IOException 如果发生 I/O 错误
         */
        T execute() throws IOException;
    }

    /**
     * 功能性接口，表示一个可能抛出 {@link IOException} 的动作操作（无返回值）。
     */
    public interface ActionWithIO {
        /**
         * 执行可能抛出 IO 异常的操作。
         *
         * @throws IOException 如果发生 I/O 错误
         */
        void execute() throws IOException;
    }
}