package org.wsp.zen.mapping.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.wsp.zen.mapping.util.MappingValidateUtils;

/**
 * 复合读取操作，将多个 {@link SegmentReadOperation} 的读取结果按顺序拼接成一个完整的字节数组。
 * <p>
 * 该类用于描述从多个不连续的窗口片段（可能来自不同的文件偏移）中读取数据，并将这些数据按指定的顺序
 * 连续地放置到一个目标字节数组中的操作。它封装了每个片段的目标数组偏移量，并提供了全面的校验：
 * <ul>
 *   <li>所有片段的存储区间必须无缝覆盖整个结果数组（从索引 0 到 {@code totalLength-1}），无重叠、无间隙。</li>
 *   <li>各片段读取长度的总和必须等于 {@code totalLength}。</li>
 *   <li>每个片段的 {@link SegmentReadOperation#targetArrayOffset} 加上其读取长度不能超出总长度。</li>
 * </ul>
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>
 * List&lt;SegmentReadOperation&gt; operations = new ArrayList&lt;&gt;();
 * operations.add(new SegmentReadOperation(segment1, 0, 0, 100));
 * operations.add(new SegmentReadOperation(segment2, 0, 100, 200));
 * CompositeReadOperation composite = new CompositeReadOperation(operations, 300);
 * byte[] result = segmentReader.read(composite);
 * </pre>
 *
 * @author wsp
 * @version 1.0
 * @see SegmentReadOperation
 */
public final class CompositeReadOperation {

    /** 不可修改的片段读取操作列表，按顺序执行 */
    public final List<SegmentReadOperation> readOperations;

    /** 合并后的总字节数 */
    public final int totalLength;

    /**
     * 构造一个复合读取操作，并对参数进行完整性校验。
     *
     * @param readOperations 片段读取操作列表，不能为 {@code null} 或空；列表中的元素将按顺序拼接
     * @param totalLength    合并后的总字节数，必须大于 0 且等于各片段读取长度之和
     * @throws NullPointerException     如果 {@code readOperations} 为 {@code null}
     * @throws IllegalArgumentException 如果列表为空、总长度不合法、存储区间不连续或有重叠、或片段区间超出结果数组范围
     */
    public CompositeReadOperation(List<SegmentReadOperation> readOperations, int totalLength) {
        this.readOperations = Collections.unmodifiableList(new ArrayList<>(readOperations));
        this.totalLength = totalLength;
        validate();
    }

    /**
     * 执行所有校验逻辑。
     * <p>校验步骤：
     * <ol>
     *   <li>校验列表非空，总长度非负。</li>
     *   <li>校验每个片段的目标偏移量 + 读取长度 ≤ 总长度。</li>
     *   <li>校验各片段读取长度之和等于总长度。</li>
     *   <li>校验各片段的存储区间（目标数组中的位置）无重叠、无间隙，且完整覆盖 [0, totalLength-1]。</li>
     * </ol>
     *
     * @throws IllegalArgumentException 如果任意校验失败
     */
    private void validate() {
        Objects.requireNonNull(readOperations, "片段列表对象不能为 null");
        if (readOperations.isEmpty()) {
            throw new IllegalArgumentException("片段列表长度为 0");
        }

        MappingValidateUtils.validateLength(totalLength);

        long actualTotalLength = 0;
        List<ArrayRange> arrayRanges = new ArrayList<>();

        for (SegmentReadOperation segmentRead : readOperations) {
            int readLength = segmentRead.getReadLength();

            if (segmentRead.targetArrayOffset + readLength > totalLength) {
                throw new IllegalArgumentException(
                        "片段存储位置超出结果数组范围：targetArrayOffset=" + segmentRead.targetArrayOffset +
                        ", 读取长度=" + readLength + ", 总长度=" + totalLength);
            }

            ArrayRange currentRange = new ArrayRange(
                    segmentRead.targetArrayOffset,
                    segmentRead.targetArrayOffset + readLength - 1);
            arrayRanges.add(currentRange);
            actualTotalLength += readLength;
        }

        if (totalLength != actualTotalLength) {
            throw new IllegalArgumentException(
                    "片段总长度不匹配：实际 " + actualTotalLength + ", 预期 " + totalLength);
        }

        validateArrayRanges(arrayRanges, totalLength);
    }

    /**
     * 校验多个数组存储区间是否连续、无重叠，且完整覆盖指定范围。
     *
     * @param arrayRanges 按顺序的区间列表（调用前需确保已排序）
     * @param totalLength 结果数组的总长度
     * @throws IllegalArgumentException 如果区间未从0开始、存在重叠或间隙、或未覆盖到 totalLength-1
     */
    private void validateArrayRanges(List<ArrayRange> arrayRanges, int totalLength) {
        Collections.sort(arrayRanges);

        if (arrayRanges.get(0).start != 0) {
            throw new IllegalArgumentException("片段存储区间存在间隙：起始位置不为0，第一个区间起始=" + arrayRanges.get(0).start);
        }

        int lastEnd = arrayRanges.get(0).end;
        for (int i = 1; i < arrayRanges.size(); i++) {
            ArrayRange current = arrayRanges.get(i);
            if (current.start <= lastEnd) {
                throw new IllegalArgumentException(
                        "片段存储区间重叠：上一个区间结束=" + lastEnd + ", 当前区间起始=" + current.start);
            }
            if (current.start != lastEnd + 1) {
                throw new IllegalArgumentException(
                        "片段存储区间存在间隙：上一个区间结束=" + lastEnd + ", 当前区间起始=" + current.start);
            }
            lastEnd = current.end;
        }

        if (lastEnd != totalLength - 1) {
            throw new IllegalArgumentException(
                    "片段存储区间未覆盖完整结果数组：最后区间结束=" + lastEnd + ", 总长度-1=" + (totalLength - 1));
        }
    }

    /**
     * 内部辅助类，记录结果数组中的一个连续存储区间。
     * <p>用于校验多个片段的目标数组位置是否连续、无重叠。
     */
    private static class ArrayRange implements Comparable<ArrayRange> {
        final int start;
        final int end;

        ArrayRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public int compareTo(ArrayRange o) {
            return Integer.compare(this.start, o.start);
        }
    }
}