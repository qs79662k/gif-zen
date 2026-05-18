package org.wsp.zen.mapping.model;

import java.util.Objects;
import java.util.SortedSet;

import org.wsp.zen.mapping.util.MappingValidateUtils;

/**
 * 表示一个连续的字节范围，以及覆盖该范围的一组有序的窗口片段。
 * <p>
 * 该类用于描述文件中一段连续的区域（从 {@code startOffset} 到 {@code endOffset} 包含两端），
 * 以及当前管理器中用于覆盖该区域的所有 {@link Segment} 片段。
 * 这些片段应当按起始偏移量升序排列，并且相互之间连续、无重叠，共同完整覆盖该连续范围。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>典型用途：</b>
 * 当需要从文件的一段连续区域中读取数据时，可以通过此类获取该区域对应的片段集合，
 * 然后利用这些片段进行实际的读取操作。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see Segment
 */
public final class ContiguousRange {

    /** 连续范围的起始偏移量（包含），≥ 0 */
    public final long startOffset;

    /** 连续范围的结束偏移量（包含），≥ startOffset */
    public final long endOffset;

    /** 覆盖该范围的所有窗口片段，按起始偏移量升序排列，不为 {@code null} */
    public final SortedSet<Segment> segments;

    /**
     * 构造一个连续范围对象。
     *
     * @param startOffset 起始偏移量（≥ 0）
     * @param endOffset   结束偏移量（≥ startOffset）
     * @param segments    覆盖该范围的窗口片段集合，必须按升序排列且不重叠，不能为 {@code null}
     * @throws IllegalArgumentException 如果起始偏移量大于结束偏移量
     * @throws NullPointerException     如果 {@code segments} 为 {@code null}
     */
    public ContiguousRange(long startOffset, long endOffset, SortedSet<Segment> segments) {
        MappingValidateUtils.validateStartOffsetAndEndOffset(startOffset, endOffset);
        this.segments = Objects.requireNonNull(segments, "片段集合对象不能为 null");
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    /**
     * 判断指定的窗口片段是否完全包含在当前连续范围内。
     *
     * @param segment 要检查的窗口片段，不能为 {@code null}
     * @return {@code true} 如果该片段的起始偏移 ≥ 当前范围的起始偏移，
     *         且该片段的结束偏移 ≤ 当前范围的结束偏移；否则返回 {@code false}
     * @throws NullPointerException 如果 {@code segment} 为 {@code null}
     */
    public boolean isContained(Segment segment) {
        return isContained(segment.startOffset, segment.endOffset);
    }

    /**
     * 判断指定的起始和结束偏移量范围是否完全包含在当前连续范围内。
     *
     * @param startOffset 起始偏移量
     * @param endOffset   结束偏移量
     * @return {@code true} 如果 {@code startOffset} ≥ 当前范围的起始偏移，
     *         且 {@code endOffset} ≤ 当前范围的结束偏移；否则返回 {@code false}
     */
    public boolean isContained(long startOffset, long endOffset) {
        return this.startOffset <= startOffset && this.endOffset >= endOffset;
    }
}