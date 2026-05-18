package org.wsp.zen.mapping.core;

import java.io.IOException;

/**
 * 带缓冲的文件内存映射接口，结合了文件映射管理（{@link MappingManager}）和
 * 数据段读取（{@link SegmentReader}）的能力。
 * <p>
 * 该接口适用于需要按需从大文件中读取特定区域，并可能对读取的数据进行缓存或预取的场景。
 * 实现类通常会维护一个或多个内存映射缓冲区，以提升随机访问性能，同时避免将整个文件加载到堆内存。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>
 * try (BufferedMapping mapping = ...) {
 *     long fileSize = mapping.size();
 *     byte[] data = mapping.readSegment(offset, length);
 *     // 处理数据...
 * }
 * </pre>
 *
 * @author wsp
 * @version 1.0
 * @see MappingManager
 * @see SegmentReader
 */
public interface BufferedMapping extends MappingManager, SegmentReader {

    /**
     * 获取当前管理的文件总大小（以字节为单位）。
     * <p>
     * 该方法通常用于确定文件边界、计算需要映射的窗口范围或验证读取偏移量的有效性。
     *
     * @return 文件的总字节数
     * @throws IOException 如果无法获取文件大小（例如文件已关闭、不存在或发生 I/O 错误）
     */
    long size() throws IOException;
}