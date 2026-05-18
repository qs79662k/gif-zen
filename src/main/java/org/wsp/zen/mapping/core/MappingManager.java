package org.wsp.zen.mapping.core;

import java.io.IOException;
import java.util.List;

import org.wsp.zen.mapping.model.Segment;
import org.wsp.zen.mapping.model.WindowChange;

/**
 * 文件映射管理器，负责管理内存映射窗口片段的映射与释放。
 * <p>
 * 该接口的实现类通常结合操作系统级的内存映射文件（如 {@link java.nio.MappedByteBuffer}）使用，
 * 支持按需映射窗口片段、批量操作以及变更应用，确保资源高效利用。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 实现类不要求全局线程安全，但应保证单个方法调用的内部一致性。
 * 若需多线程并发访问同一文件区域，建议外部同步或使用独立实例。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * try (MappingManager manager = ...) {
 *     Segment segment = new Segment(offset, length);
 *     manager.map(segment);
 *     byte[] data = manager.readSegment(segment.getOffset(), segment.getLength());
 *     manager.release(segment);
 * }
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see Segment
 * @see WindowChange
 */
public interface MappingManager extends AutoCloseable {

    /**
     * 将指定的窗口片段映射到内存缓冲区。
     * <p>
     * 实现类应确保如果该片段已经映射，则此操作可能是幂等的（或抛出异常，由实现决定）。
     * 映射完成后，可以通过其他读取方法访问该区域的数据。
     * </p>
     *
     * @param segment 需要映射的窗口片段，不能为 {@code null}
     * @throws IOException 如果映射过程中发生 I/O 错误（如文件无法读取、偏移量非法）
     * @throws NullPointerException 如果 {@code segment} 为 {@code null}
     */
    void map(Segment segment) throws IOException;

    /**
     * 批量将多个窗口片段映射到内存缓冲区。
     * <p>
     * 该方法提供原子性或顺序性（由实现决定），建议实现保证操作的顺序性，即依次映射每个片段，
     * 如果某个片段映射失败，可以选择停止并抛出异常，或者继续映射剩余片段。
     * </p>
     *
     * @param segments 需要映射的窗口片段列表，不能为 {@code null}，列表元素不能为 {@code null}
     * @throws IOException 如果映射过程中发生 I/O 错误
     * @throws NullPointerException 如果 {@code segments} 或其中任何元素为 {@code null}
     */
    void map(List<Segment> segments) throws IOException;

    /**
     * 释放指定窗口片段的映射资源。
     * <p>
     * 释放后，该片段对应的内存缓冲区可能变得不可用，再次读取该区域应重新映射。
     * 如果该片段未被映射，实现通常应忽略此操作（不抛出异常）。
     * </p>
     *
     * @param segment 需要释放的窗口片段，不能为 {@code null}
     * @throws NullPointerException 如果 {@code segment} 为 {@code null}
     */
    void release(Segment segment);

    /**
     * 批量释放多个窗口片段的映射资源。
     * <p>
     * 实现应顺序释放每个片段，即使某个片段释放失败（如未映射），也应继续处理后续片段。
     * </p>
     *
     * @param segments 需要释放的窗口片段列表，不能为 {@code null}，列表元素不能为 {@code null}
     * @throws NullPointerException 如果 {@code segments} 或其中任何元素为 {@code null}
     */
    void release(List<Segment> segments);

    /**
     * 应用窗口片段的变更，批量处理释放和映射操作。
     * <p>
     * 该方法根据变更对象中的信息，先释放需要移除的旧片段，再映射需要新增的新片段，
     * 确保资源切换的一致性和顺序性。典型的用途是在滑动窗口时动态调整映射区域。
     * </p>
     *
     * @param changes 窗口片段变更对象，包含需要释放的旧片段列表和需要映射的新片段列表，不能为 {@code null}
     * @throws IOException 如果映射过程中发生 I/O 错误
     * @throws NullPointerException 如果 {@code changes} 为 {@code null}
     * @see WindowChange#getSegmentsToRemove()
     * @see WindowChange#getSegmentsToAdd()
     */
    void applyChanges(WindowChange changes) throws IOException;

    /**
     * 校验指定的窗口片段是否已被映射。
     *
     * @param segment 需要检查的窗口片段，不能为 {@code null}
     * @return {@code true} 表示该片段当前已被映射且可用；{@code false} 表示未映射
     * @throws NullPointerException 如果 {@code segment} 为 {@code null}
     */
    boolean isMapped(Segment segment);

    /**
     * 清空所有已映射的片段，释放所有相关资源。
     * <p>
     * 调用后，所有之前映射的片段都将被释放，管理器恢复到无映射状态。
     * 此操作不会关闭管理器本身。
     * </p>
     */
    void clear();

    /**
     * 关闭管理器，释放所有关联资源（包括所有已映射的缓冲区）。
     * <p>
     * 关闭后，管理器不可再使用。任何后续操作（如 {@link #map}、{@link #release} 等）
     * 都应抛出 {@link IOException} 或 {@link IllegalStateException}。
     * </p>
     *
     * @throws IOException 如果关闭过程中发生 I/O 错误（例如无法释放系统资源）
     */
    @Override
    void close() throws IOException;
}