package org.wsp.zen.mapping.core;

import java.io.IOException;

import org.wsp.zen.mapping.model.MappingContext;

/**
 * 文件内存映射管理器，负责管理大文件的按需内存映射窗口，并提供高效的随机读取能力。
 * <p>
 * 该接口适用于需要频繁读取大文件不同区域，但无法将整个文件加载到内存的场景。
 * 实现类通常基于 {@link java.nio.MappedByteBuffer} 或类似技术，支持动态调整映射窗口，
 * 并自动处理窗口缺失时的重映射（通过 {@link #readWithAutoRecovery} 系列方法）。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 实现类不要求全局线程安全，但通常应保证单个方法调用的内部一致性。
 * 如果多线程并发调用，建议外部同步或使用专用实例。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * try (FileMappingManager manager = ...) {
 *     // 预先配置窗口映射参数
 *     MappingContext request = new MappingContext.Builder()
 *             .withMappingDirection(MappingDirection.FORWARD)
 *             .withWindowBaseOffset(0)
 *             .withWindowSize(1024 * 1024)
 *             .build();
 *     manager.remapWindow(request);
 *
 *     // 读取数据（如果窗口已覆盖，直接读取；否则自动重映射）
 *     byte[] data = manager.readWithAutoRecovery(1000, 500, request);
 * }
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see MappingContext
 * @see java.nio.MappedByteBuffer
 */
public interface FileMappingManager extends AutoCloseable {

    /**
     * 重新映射文件内存窗口，根据映射请求参数调整映射范围和方向。
     * <p>
     * 该方法会改变当前活动的内存映射窗口，后续的读取操作（如 {@link #read(long, int)}）
     * 将基于新窗口进行。如果新窗口与旧窗口有重叠，实现类应尽量复用已有映射。
     * </p>
     *
     * @param mappingContext 窗口映射请求对象，包含映射方向、基准偏移量和窗口大小等信息，不能为 {@code null}
     * @throws IOException              如果映射操作发生 I/O 错误（如文件无法访问、偏移量非法）
     * @throws NullPointerException     如果 {@code mappingContext} 为 {@code null}
     */
    void remapWindow(MappingContext mappingContext) throws IOException;

    /**
     * 从文件指定位置读取数据，返回新字节数组。
     * <p>
     * 该方法假定当前内存映射窗口已覆盖所需区域（通常由之前的 {@link #remapWindow(MappingContext)} 保证）。
     * 若未覆盖，实现可能抛出异常或返回不完整数据。推荐使用 {@link #readWithAutoRecovery} 以自动处理窗口缺失。
     * </p>
     *
     * @param startOffset 起始偏移量（≥ 0 且不超出文件大小）
     * @param length      读取长度（> 0 且不超出文件剩余长度）
     * @return 读取的字节数组，长度为实际读取到的数据量（可能小于 {@code length}，若到达文件末尾）；
     *         如果 {@code length == 0}，可返回空数组；如果无数据且已到文件末尾，返回 {@code null} 或空数组（具体由实现决定）
     * @throws IOException              如果读取发生 I/O 错误（如窗口未覆盖、文件关闭等）
     * @throws IllegalArgumentException 如果 {@code startOffset < 0} 或 {@code length < 0}
     */
    byte[] read(long startOffset, int length) throws IOException;

    /**
     * 从文件指定位置读取数据，并将结果写入目标字节数组。
     * <p>
     * 该方法将数据读入调用者提供的数组，避免额外内存分配。同样假定当前内存映射窗口已覆盖所需区域。
     * </p>
     *
     * @param startOffset  文件读取起始偏移量（≥ 0 且不超出文件大小）
     * @param length       计划读取的字节数（> 0）
     * @param b            目标字节数组，不能为 {@code null}
     * @param targetOffset 数据写入目标数组的起始位置（≥ 0 且 {@code targetOffset + length <= b.length}）
     * @return 实际读取的字节数（可能小于 {@code length}，若到达文件末尾）；如果已到达文件末尾且 {@code length > 0}，返回 -1
     * @throws IOException              如果读取发生 I/O 错误
     * @throws NullPointerException     如果 {@code b} 为 {@code null}
     * @throws IllegalArgumentException 如果参数范围非法（如偏移量为负、超出数组边界等）
     */
    int read(long startOffset, int length, byte[] b, int targetOffset) throws IOException;

    /**
     * 带窗口重映射的读取操作，自动处理窗口缺失场景，返回新字节数组。
     * <p>
     * 当检测到当前内存映射窗口不包含请求的区域时，该方法会根据 {@code mappingContext} 自动调用
     * {@link #remapWindow(MappingContext)} 调整窗口，然后重新尝试读取。适用于顺序播放或滑动窗口场景。
     * </p>
     *
     * @param startOffset    文件读取起始偏移量（≥ 0）
     * @param length         计划读取的字节数（> 0）
     * @param mappingContext 窗口映射请求对象，包含映射所需的全部参数（方向、基准偏移量、窗口大小等），不能为 {@code null}
     * @return 读取的字节数组（长度为实际读取到的数据量），如果无数据且已到文件末尾，返回 {@code null}
     * @throws IOException              如果读取或重映射发生 I/O 错误
     * @throws NullPointerException     如果 {@code mappingContext} 为 {@code null}
     * @throws IllegalArgumentException 如果 {@code startOffset < 0} 或 {@code length < 0}
     */
    byte[] readWithAutoRecovery(
            long startOffset,
            int length,
            MappingContext mappingContext) throws IOException;

    /**
     * 带窗口重映射的读取操作，自动处理窗口缺失场景，结果写入目标字节数组。
     * <p>
     * 与 {@link #readWithAutoRecovery(long, int, MappingContext)} 类似，但将数据写入调用者提供的数组，
     * 避免额外内存分配。
     * </p>
     *
     * @param startOffset    文件读取起始偏移量（≥ 0）
     * @param length         计划读取的字节数（≥ 0 且不超过数组可用空间）
     * @param b              目标字节数组，不能为 {@code null}
     * @param targetOffset   数据写入目标数组的起始位置（≥ 0 且 {@code targetOffset + length <= b.length}）
     * @param mappingContext 窗口映射请求对象，包含映射方向、基准偏移量和窗口大小等信息，不能为 {@code null}
     * @return 实际读取的字节数（可能小于 {@code length}，若到达文件末尾）；如果已到达文件末尾且 {@code length > 0}，返回 -1
     * @throws IOException              如果读取或重映射发生 I/O 错误
     * @throws NullPointerException     如果 {@code b} 或 {@code mappingContext} 为 {@code null}
     * @throws IllegalArgumentException 如果参数范围非法
     */
    int readWithAutoRecovery(
            long startOffset,
            int length,
            byte[] b,
            int targetOffset,
            MappingContext mappingContext) throws IOException;

    /**
     * 获取当前管理的文件总大小（字节）。
     *
     * @return 文件总字节数
     * @throws IOException 如果获取大小发生 I/O 错误（如文件已关闭）
     */
    long size() throws IOException;

    /**
     * 清除所有内存映射数据和状态，释放相关资源。
     * <p>
     * 调用后，所有已映射的窗口都会被释放，管理器恢复到未映射状态，但文件通道保持打开。
     * 后续可以重新调用 {@link #remapWindow(MappingContext)} 建立新窗口。
     * </p>
     *
     * @throws IOException 如果清除操作发生 I/O 错误
     */
    void clear() throws IOException;

    /**
     * 释放所有资源并关闭管理器。
     * <p>
     * 关闭后，所有已映射窗口被释放，文件通道被关闭，管理器不可再使用。
     * 重复调用此方法应无副作用（幂等）。
     * </p>
     *
     * @throws IOException 如果释放资源发生 I/O 错误
     */
    @Override
    void close() throws IOException;
}