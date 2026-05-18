package org.wsp.zen.mapping.core;

import org.wsp.zen.mapping.exception.MappingMismatchException;
import org.wsp.zen.mapping.model.CompositeReadOperation;
import org.wsp.zen.mapping.model.Segment;

/**
 * 窗口片段读取器，负责从文件映射的窗口片段中读取原始数据。
 * <p>
 * 该接口的实现类通常配合内存映射文件或缓存使用，支持单片段读取和多片段合并读取。
 * 它提供了三种读取方式：
 * <ul>
 *   <li>从单个窗口片段中读取一段连续数据到目标字节数组的指定位置。</li>
 *   <li>根据复合读取操作，将多个片段的数据合并成一个完整的字节数组返回。</li>
 *   <li>根据复合读取操作，将多个片段的数据直接写入指定的目标字节数组。</li>
 * </ul>
 * 实现类应确保读取操作是线程安全的（如果允许多线程访问）。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 实现类应保证线程安全，允许并发读取操作。如果实现依赖于可变的内部状态，需适当同步。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * SegmentReader reader = ...;
 * Segment segment = new Segment(offset, length);
 * byte[] buffer = new byte[length];
 * reader.read(segment, 0, buffer, 0, length);
 *
 * // 复合读取
 * CompositeReadOperation composite = ...;
 * byte[] data = reader.read(composite);
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see Segment
 * @see CompositeReadOperation
 * @see MappingMismatchException
 */
public interface SegmentReader {

    /**
     * 从指定的窗口片段中读取一段连续数据到目标字节数组。
     * <p>
     * 该方法从片段内的 {@code startOffset} 位置开始，读取 {@code length} 个字节，
     * 并将其写入目标数组的 {@code offset} 位置。
     * 调用前需确保该片段已经通过 {@link MappingManager#map(Segment)} 映射到内存。
     * </p>
     *
     * @param segment     要读取的窗口片段，不能为 {@code null}
     * @param startOffset 片段内的起始偏移量（从 0 开始计数），必须满足 {@code 0 <= startOffset < segment.getLength()}
     * @param target      用于接收数据的目标字节数组，不能为 {@code null}
     * @param offset      目标数组中的起始存放位置，必须满足 {@code 0 <= offset <= target.length - length}
     * @param length      需要读取的字节数，必须大于 0
     * @throws NullPointerException      如果 {@code segment} 或 {@code target} 为 {@code null}
     * @throws IllegalArgumentException 当以下情况时：
     *                                   <ul>
     *                                     <li>{@code length <= 0}</li>
     *                                     <li>{@code startOffset < 0} 或 {@code startOffset >= segment.getLength()}</li>
     *                                     <li>{@code startOffset + length > segment.getLength()}</li>
     *                                     <li>{@code offset < 0} 或 {@code offset + length > target.length}</li>
     *                                   </ul>
     * @throws MappingMismatchException   当底层文件映射与请求的窗口片段不一致或读取失败时抛出
     */
    void read(Segment segment, int startOffset, byte[] target, int offset, int length) throws MappingMismatchException;

    /**
     * 根据复合读取操作，从多个窗口片段中读取数据，并将所有片段的数据按顺序合并成一个完整的字节数组返回。
     * <p>
     * 该方法适用于需要从多个不连续的片段中读取连续逻辑数据块的场景。
     * 实现类应按 {@link CompositeReadOperation#readOperations} 返回的顺序依次读取每个片段，
     * 忽略片段间的间隙，直接拼接数据。
     * </p>
     *
     * @param composite 复合读取操作对象，包含待读取的片段列表及总长度，不能为 {@code null}
     * @return 包含所有片段合并数据的字节数组，长度等于 {@code composite.totalLength}
     * @throws NullPointerException     如果 {@code composite} 为 {@code null}
     * @throws MappingMismatchException 当任何一个片段读取失败时抛出（例如片段未映射或读取偏移非法）
     */
    byte[] read(CompositeReadOperation composite) throws MappingMismatchException;

    /**
     * 根据复合读取操作，从多个窗口片段中读取数据，并将数据直接写入指定的目标字节数组。
     * <p>
     * 该方法与 {@link #read(CompositeReadOperation)} 类似，但允许调用者复用已有的字节数组，
     * 避免额外的内存分配。数据将按顺序写入目标数组的 {@code offset} 起始位置。
     * </p>
     *
     * @param composite 复合读取操作对象，包含待读取的片段列表及总长度，不能为 {@code null}
     * @param target    用于接收数据的目标字节数组，长度必须至少为 {@code composite.totalLength} + {@code offset}
     * @param offset    目标数组中的起始存放位置，必须满足 {@code 0 <= offset <= target.length - composite.totalLength}
     * @throws NullPointerException      如果 {@code composite} 或 {@code target} 为 {@code null}
     * @throws IllegalArgumentException 如果目标数组剩余容量不足（{@code offset < 0} 或 {@code offset + composite.totalLength > target.length}）
     * @throws MappingMismatchException 如果任何一个片段读取失败时抛出
     */
    void read(CompositeReadOperation composite, byte[] target, int offset) throws MappingMismatchException;
}