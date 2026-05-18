package org.wsp.zen.mapping.model;

import java.util.Objects;

import org.wsp.zen.mapping.util.MappingValidateUtils;

/**
 * 窗口片段，表示文件中的一个连续数据区间。
 * <p>
 * 该类描述了一段连续的字节范围，从 {@code startOffset} 到 {@code endOffset}（包含两端），
 * 并实现了 {@link Comparable} 接口，默认按起始偏移量升序排列，起始相同时按结束偏移量升序。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>主要用途：</b>
 * <ul>
 *   <li>表示内存映射窗口中的一个连续片段。</li>
 *   <li>支持包含判断（{@link #isContained(Segment)}）、重叠判断（{@link #isOverlapping(Segment)}）。</li>
 *   <li>提供获取片段内相对位置的方法（{@link #getBufferPosition(long)}）。</li>
 *   <li>用于窗口片段管理器中的片段集合操作。</li>
 * </ul>
 *
 * @author wsp
 * @version 1.0
 * @see #compareTo(Segment)
 */
public final class Segment implements Comparable<Segment> {

    // ==================== 字段 ====================

    /** 片段起始偏移量（包含），≥ 0 */
    public final long startOffset;

    /** 片段结束偏移量（包含），≥ startOffset */
    public final long endOffset;

    // ==================== 构造器与校验 ====================

    /**
     * 构造一个窗口片段。
     *
     * @param startOffset 起始偏移量（≥ 0）
     * @param endOffset   结束偏移量（≥ startOffset）
     * @throws IllegalArgumentException 如果起始偏移量 > 结束偏移量，或片段长度超过 {@link Integer#MAX_VALUE}
     */
    public Segment(long startOffset, long endOffset) {
        validate(startOffset, endOffset);
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    /**
     * 校验参数合法性。
     *
     * @param startOffset 起始偏移量
     * @param endOffset   结束偏移量
     * @throws IllegalArgumentException 如果起始 > 结束，或长度超出 int 范围
     */
    private void validate(long startOffset, long endOffset) {
        MappingValidateUtils.validateStartOffsetAndEndOffset(startOffset, endOffset);
        MappingValidateUtils.validateLengthWithinIntMax(endOffset - startOffset);
    }

    // ==================== 包含与重叠判断 ====================

    /**
     * 判断当前片段是否完全包含目标片段。
     *
     * @param targetSegment 目标窗口片段，不能为 {@code null}
     * @return {@code true} 如果当前片段的起始 ≤ 目标片段的起始，且当前片段的结束 ≥ 目标片段的结束
     * @throws NullPointerException 如果 {@code targetSegment} 为 {@code null}
     */
    public boolean isContained(Segment targetSegment) {
        return isContained(targetSegment.startOffset, targetSegment.endOffset);
    }

    /**
     * 判断当前片段是否完全包含指定的起始和结束偏移量范围。
     *
     * @param targetStartOffset 目标起始偏移量
     * @param targetEndOffset   目标结束偏移量
     * @return {@code true} 如果当前片段完全包含该范围
     */
    public boolean isContained(long targetStartOffset, long targetEndOffset) {
        return startOffset <= targetStartOffset && endOffset >= targetEndOffset;
    }

    /**
     * 判断当前片段是否与目标片段存在重叠。
     *
     * @param targetSegment 目标窗口片段，不能为 {@code null}
     * @return {@code true} 如果两片段有重叠区域（包括边界相接的情况）
     * @throws NullPointerException 如果 {@code targetSegment} 为 {@code null}
     */
    public boolean isOverlapping(Segment targetSegment) {
        return isOverlapping(targetSegment.startOffset, targetSegment.endOffset);
    }

    /**
     * 判断当前片段是否与指定的起始和结束偏移量范围存在重叠。
     *
     * @param targetStartOffset 目标起始偏移量
     * @param targetEndOffset   目标结束偏移量
     * @return {@code true} 如果有重叠
     */
    public boolean isOverlapping(long targetStartOffset, long targetEndOffset) {
        return !(startOffset > targetEndOffset || endOffset < targetStartOffset);
    }

    // ==================== 片段内位置计算 ====================

    /**
     * 获取目标起始偏移量在当前片段内的相对位置（字节偏移）。
     * <p>
     * 例如，当前片段覆盖 [100, 199]，目标起始偏移量为 105，则返回 5。
     *
     * @param targetStartOffset 目标起始偏移量，必须在当前片段的范围内（包含两端）
     * @return 目标起始偏移量相对于当前片段起始偏移量的偏移值（从 0 开始）
     * @throws IllegalArgumentException 如果目标起始偏移量不在 [startOffset, endOffset] 范围内
     */
    public int getBufferPosition(long targetStartOffset) {
        if (targetStartOffset < startOffset || targetStartOffset > endOffset) {
            throw new IllegalArgumentException(
                    "目标位置超出片段范围：" + targetStartOffset +
                    " 未在 [" + startOffset + "," + endOffset + "] 范围内");
        }
        
        return (int) (targetStartOffset - startOffset);
    }

    // ==================== 尺寸与信息 ====================

    /**
     * 返回当前片段的字节长度。
     *
     * @return 片段包含的字节数（≥ 1）
     */
    public long size() {
        return endOffset - startOffset + 1;
    }

    @Override
    public String toString() {
        return "Segment {" +
                "startOffset=" + startOffset +
                ", endOffset=" + endOffset +
                ", size=" + size() + '}';
    }

    // ==================== 相等性与比较 ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Segment)) return false;
        Segment segment = (Segment) o;
        return startOffset == segment.startOffset && endOffset == segment.endOffset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startOffset, endOffset);
    }

    /**
     * 比较两个窗口片段，先按起始偏移量升序，再按结束偏移量升序。
     *
     * @param targetSegment 要比较的目标片段
     * @return 负值、零或正值，分别表示当前片段小于、等于或大于目标片段
     */
    @Override
    public int compareTo(Segment targetSegment) {
        if (targetSegment == null) {
            return 1;
        }
        int startCompare = Long.compare(startOffset, targetSegment.startOffset);
        if (startCompare != 0) {
            return startCompare;
        }
        return Long.compare(endOffset, targetSegment.endOffset);
    }
}