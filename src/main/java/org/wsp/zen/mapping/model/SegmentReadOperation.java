package org.wsp.zen.mapping.model;

import java.util.Objects;

import org.wsp.zen.mapping.util.MappingValidateUtils;

/**
 * 片段读取操作，描述从单个窗口片段中读取一段连续数据的相关信息。
 * <p>
 * 该类封装了从某个已映射的窗口片段（{@link Segment}）中读取一段连续子范围的操作，
 * 并指定该段数据在最终结果数组中的目标位置。它用于构建更复杂的复合读取操作（如 {@link CompositeReadOperation}）。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>字段关系：</b>
 * <ul>
 *   <li>读取的片段内范围：从 {@code startOffset} 到 {@code endOffset}（包含两端），长度 = {@code endOffset - startOffset + 1}。</li>
 *   <li>数据在结果数组中的起始位置：{@code targetArrayOffset}，结果数组需保证有足够空间（至少到 {@code targetArrayOffset + 读取长度 - 1}）。</li>
 * </ul>
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>
 * Segment segment = new Segment(0, 1024);
 * SegmentReadOperation op = new SegmentReadOperation(segment, 100, 199, 0);
 * // 表示从 segment 的偏移 100 到 199（共100字节）读取，放入结果数组的起始位置 0
 * </pre>
 *
 * @author wsp
 * @version 1.0
 * @see Segment
 * @see CompositeReadOperation
 */
public final class SegmentReadOperation {

    /** 窗口片段引用，表示数据所在的已映射文件区域 */
    public final Segment segment;

    /** 需从片段里读取数据的起始偏移（片段内相对偏移，从 0 开始计数） */
    public final int startOffset;

    /** 需从片段里读取数据的结束偏移（包含，片段内相对偏移） */
    public final int endOffset;

    /** 在结果数组中的起始位置（目标数组索引，从 0 开始） */
    public final int targetArrayOffset;

    /**
     * 构造一个片段读取操作。
     *
     * @param segment           窗口片段，不能为 {@code null}
     * @param startOffset       片段内起始偏移量（≥ 0 且 ≤ endOffset）
     * @param endOffset         片段内结束偏移量（≥ startOffset 且 < segment.size()）
     * @param targetArrayOffset 结果数组中的起始位置（≥ 0）
     * @throws NullPointerException     如果 {@code segment} 为 {@code null}
     * @throws IllegalArgumentException 如果参数范围不合法（如偏移量超出片段范围、起始大于结束等）
     */
    public SegmentReadOperation(Segment segment, int startOffset, int endOffset, int targetArrayOffset) {
        this.segment = segment;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.targetArrayOffset = targetArrayOffset;
        validate();
    }

    /**
     * 校验参数合法性：
     * <ul>
     *   <li>片段非空</li>
     *   <li>起始偏移 ≤ 结束偏移</li>
     *   <li>起始和结束偏移在片段范围内（0 到片段大小-1）</li>
     *   <li>目标数组偏移非负</li>
     *   <li>读取长度 > 0</li>
     * </ul>
     *
     * @throws IllegalArgumentException 如果任意校验失败
     */
    private void validate() {
        Objects.requireNonNull(segment, "窗口片段对象不能为 null");

        MappingValidateUtils.validateStartOffsetAndEndOffset(startOffset, endOffset);

        long fragmentSize = segment.size();
        MappingValidateUtils.validateRangeArguments(startOffset, getReadLength(), fragmentSize);

        MappingValidateUtils.validateTargetOffset(targetArrayOffset);

        MappingValidateUtils.validateLength(getReadLength());
    }

    /**
     * 获取本次读取操作的长度（字节数）。
     *
     * @return 读取的字节数，等于 {@code endOffset - startOffset + 1}
     */
    public int getReadLength() {
        return (endOffset - startOffset + 1);
    }
}