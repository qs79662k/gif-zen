package org.wsp.zen.gif.core;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * GIF 源解析策略接口，将字符串形式的源（本地路径、URL、Data URI）
 * 转换为可供解码器读取的输入流，并暴露文件系统路径用于后续高性能操作（如内存映射）。
 * <p>
 * 缓存目录等存储细节由各实现自行管理（例如建造者模式配置），接口方法保持极简，
 * 只暴露 {@link #resolve(String)} 一个方法，确保解码器等上层组件完全与存储策略解耦。
 * </p>
 * <p>
 * 接口及其内部结果类均基于 Java 标准库最基础类型（{@link String}、{@link InputStream}、
 * {@link File}），确保同一套代码可在桌面、Android、鸿蒙等所有 Java 平台无缝运行。
 * 移动端只需提供本接口的自定义实现，而无需触碰解码器等核心组件。
 * </p>
 *
 * <p><b>典型用法（桌面默认）：</b>
 * <pre>{@code
 * SourceResolver resolver = new DefaultSourceResolver();
 * SourceResult result = resolver.resolve("http://example.com/demo.gif");
 * try (InputStream in = result.inputStream()) {
 *     // 使用流
 * }
 * String path = result.cachePath();  // 可用于文件映射
 * result.deleteCacheFile();          // 清理临时文件
 * }</pre>
 * </p>
 *
 * @author wsp
 * @version 3.0
 * @see DefaultSourceResolver
 */
public interface SourceResolver {

    /**
     * 解析源，使用实现内部配置的缓存目录（或其他存储策略）。
     *
     * @param source 源字符串（本地文件路径、HTTP/HTTPS/FTP URL 或 Data URI）
     * @return 包含打开的输入流和文件路径的解析结果，绝不返回 {@code null}
     * @throws IOException 解析失败（文件不存在、网络错误等）
     */
    SourceResult resolve(String source) throws IOException;

    // ==================== 结果类 ====================
    /**
     * 解析结果值对象，持有从源获取的输入流及其在文件系统上的完整路径。
     * <p>
     * 此类被设计为具体类（不可继承）以简化跨平台实现：
     * 所有方法均基于 {@link String}、{@link InputStream} 和 {@link File}，
     * 在任何 Java 环境中均可直接使用，无需额外抽象。
     * </p>
     */
    final class SourceResult implements Closeable {
        private final InputStream inputStream;
        private final String cachePath;   // 文件系统路径（可能为缓存文件）

        /**
         * 构造解析结果。
         *
         * @param inputStream 已打开的输入流（可能包装了缓存回写或位置跟踪）
         * @param cachePath   与该流对应的文件系统绝对路径（本地文件或缓存临时文件）
         * @throws NullPointerException 如果任一参数为 {@code null}
         */
        public SourceResult(InputStream inputStream, String cachePath) {
            this.inputStream = Objects.requireNonNull(inputStream, "inputStream 不能为 null");
            this.cachePath = Objects.requireNonNull(cachePath, "cachePath 不能为 null");
        }

        /**
         * 返回可读取 GIF 数据的输入流。
         * <p>
         * 注意：该流可能已包装了位置跟踪（{@code PositionTrackingInputStream}）
         * 或自动缓存写入（{@code CacheInputStream}），调用者无需关心内部细节。
         * </p>
         *
         * @return 输入流，不为 {@code null}
         */
        public InputStream inputStream() {
            return inputStream;
        }

        /**
         * 返回与该源对应的文件系统绝对路径。
         * <ul>
         *   <li>本地文件：返回其原始绝对路径</li>
         *   <li>网络资源或 Data URI：返回下载/解码后缓存的临时文件绝对路径</li>
         * </ul>
         * 该路径可直接传递给文件映射管理器（{@code FileMappingManagerFactory}）
         * 进行内存映射或随机读取。
         *
         * @return 平台无关的绝对路径字符串，例如 {@code "/home/user/.zen-cache/zen_url_abc123.cache"}
         */
        public String cachePath() {
            return cachePath;
        }

        /**
         * 删除与此结果关联的缓存文件（如果存在）。
         * <p>
         * 调用后该文件将不可用。通常用于缓存清理或错误恢复场景。
         * 基于 {@link File#delete()} 实现，所有平台行为一致。
         * </p>
         */
        public void deleteCacheFile() {
            File file = new File(cachePath);
            if (file.exists()) {
                file.delete();
            }
        }

        /**
         * 关闭底层输入流并释放系统资源。
         * 关闭后不应再从 {@link #inputStream()} 读取数据。
         *
         * @throws IOException 如果关闭流时发生 I/O 错误
         */
        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}