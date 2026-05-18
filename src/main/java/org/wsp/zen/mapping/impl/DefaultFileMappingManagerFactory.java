package org.wsp.zen.mapping.impl;

import java.io.IOException;
import java.util.Objects;

import org.wsp.zen.mapping.core.FileMappingManager;
import org.wsp.zen.mapping.core.FileMappingManagerFactory;

/**
 * 桌面环境的默认 {@link FileMappingManagerFactory} 实现。
 * <p>
 * 接收平台无关的字符串路径，直接传递给 {@link DefaultFileMappingManager.Builder}，
 * 由 Builder 内部转换为 {@link java.nio.file.Path} 以利用 NIO 内存映射。
 * 使用默认配置（包括默认的窗口片段管理器、缓冲区管理器和并发控制器）。
 * </p>
 * <p>
 * 如果需要自定义映射管理器配置（如自定义缓冲区实现），可直接使用
 * {@link DefaultFileMappingManager.Builder} 进行构建，而不必使用本工厂。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该工厂是无状态的，因此是线程安全的。{@link #create(String)} 方法可被多线程并发调用。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * FileMappingManagerFactory factory = new DefaultFileMappingManagerFactory();
 * String filePath = "/home/user/animation.gif";
 * try (FileMappingManager manager = factory.create(filePath)) {
 *     manager.remapWindow(mappingContext);
 *     byte[] data = manager.readWithAutoRecovery(offset, length, mappingContext);
 * }
 * }</pre>
 *
 * <p><b>注意：</b>
 * 本实现依赖 {@link DefaultFileMappingManager} 内部对 NIO 的使用，因此仅适用于标准 JDK
 * 桌面或服务器环境。移动端或其他不支持 NIO 的平台需提供替代工厂实现。
 * </p>
 *
 * @author wsp
 * @version 1.2
 * @see FileMappingManagerFactory
 * @see DefaultFileMappingManager
 */
public class DefaultFileMappingManagerFactory implements FileMappingManagerFactory {

    /**
     * 基于文件路径字符串创建一个新的 {@link FileMappingManager} 实例。
     * <p>
     * 创建的管理器处于初始状态（尚未映射任何窗口），调用者需要后续调用
     * {@link FileMappingManager#remapWindow} 来建立内存映射窗口。
     * </p>
     *
     * @param filePath 需要映射的文件路径（平台无关字符串），不能为 {@code null}，
     *                 且文件必须存在且可读
     * @return 新的文件映射管理器实例，不为 {@code null}
     * @throws IOException          如果无法打开文件（例如文件不存在、无读取权限）或初始化失败
     * @throws NullPointerException 如果 {@code filePath} 为 {@code null}
     */
    @Override
    public FileMappingManager create(String filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath 不能为 null");
        return new DefaultFileMappingManager.Builder(filePath).build();
    }
}