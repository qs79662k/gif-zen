package org.wsp.zen.mapping.core;

import java.io.IOException;

/**
 * 文件映射管理器工厂接口，用于创建针对特定文件的 {@link FileMappingManager} 实例。
 * <p>
 * 该工厂允许将文件映射管理器的创建逻辑与具体实现解耦，便于在不同平台或不同策略下
 * 提供不同的管理器实现（例如基于内存映射文件、基于常规 I/O 等）。
 * 路径使用 {@link String} 传递以保证跨平台兼容（桌面、Android 等均原生支持）。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 工厂实现类应保证线程安全，因为 {@link #create(String)} 方法可能被多个线程并发调用。
 * 通常，无状态工厂是天然线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * FileMappingManagerFactory factory = new DefaultFileMappingManagerFactory();
 * String gifPath = "/path/to/animation.gif";
 * try (FileMappingManager manager = factory.create(gifPath)) {
 *     // 使用 manager 进行文件映射和读取操作
 *     manager.remapWindow(mappingContext);
 *     byte[] data = manager.readWithAutoRecovery(offset, length, mappingContext);
 * }
 * }</pre>
 *
 * @author wsp
 * @version 1.1
 * @see FileMappingManager
 */
public interface FileMappingManagerFactory {

    /**
     * 创建一个针对指定文件路径的 {@link FileMappingManager} 实例。
     * <p>
     * 创建的管理器应处于初始状态（尚未映射任何窗口），调用者需要后续调用
     * {@link FileMappingManager#remapWindow} 来建立内存映射窗口。
     * </p>
     *
     * @param filePath 需要映射的文件路径（平台无关字符串），不能为 {@code null}，
     *                 且文件必须存在且可读
     * @return 新的文件映射管理器实例，不为 {@code null}
     * @throws IOException          如果无法打开文件（例如文件不存在、无读取权限）或初始化失败
     * @throws NullPointerException 如果 {@code filePath} 为 {@code null}
     */
    FileMappingManager create(String filePath) throws IOException;
}