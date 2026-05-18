package org.wsp.zen.mapping.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.wsp.zen.mapping.core.MappingDirection;
import org.wsp.zen.mapping.core.WindowPolicy;
import org.wsp.zen.mapping.model.MappingContext;
import org.wsp.zen.mapping.model.Segment;

/**
 * 默认的窗口片段计算策略实现。
 * <p>
 * 该策略根据映射请求（包括文件大小、窗口大小、方向及基准偏移量）计算所需的窗口片段列表。
 * 主要功能：
 * <ul>
 *   <li>根据方向（正向/反向）计算窗口的起始和结束偏移量。</li>
 *   <li>自动处理文件边界环绕（当窗口超出文件末尾或开头时，自动拆分出回绕部分）。</li>
 *   <li>将过大的窗口（超过 {@value #MAX_SEGMENT_SIZE} 字节）拆分为多个连续的窗口片段，
 *       以避免超出内存映射的单个缓冲区大小限制。</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该策略类是无状态的，因此是线程安全的。{@link #calculateWindowFragments(MappingContext)} 方法
 * 可被多线程并发调用。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see WindowPolicy
 * @see MappingContext
 * @see Segment
 */
public class DefaultWindowPolicy implements WindowPolicy {

    /**
     * 单个窗口片段的最大字节长度。
     * <p>
     * 受限于 {@link java.nio.MappedByteBuffer} 的大小限制（不能超过 {@link Integer#MAX_VALUE}），
     * 因此将每个片段的最大长度设为 {@code Integer.MAX_VALUE} 字节。
     * </p>
     */
    private static final long MAX_SEGMENT_SIZE = Integer.MAX_VALUE;

    // ==================== 窗口片段计算入口 ====================

    /**
     * 根据映射请求计算需要映射的窗口片段列表。
     * <p>
     * 计算步骤：
     * <ol>
     *   <li>校验请求不为 {@code null}。</li>
     *   <li>计算调整后的窗口大小（不能超过文件总大小）。</li>
     *   <li>根据映射方向（正向/反向）计算窗口的起始偏移和结束偏移。</li>
     *   <li>将计算出的窗口范围（可能包含文件边界回绕）拆分为多个不超过 {@link #MAX_SEGMENT_SIZE} 的窗口片段。</li>
     * </ol>
     * </p>
     *
     * @param mappingContext 窗口映射请求，包含文件大小、窗口大小、方向及基准偏移等信息，不能为 {@code null}
     * @return 窗口片段列表（可能为空，如果调整后的窗口大小为 0）；列表中的片段按文件顺序排列
     * @throws NullPointerException 如果 {@code mappingContext} 为 {@code null}
     */
    @Override
    public List<Segment> calculateWindowFragments(MappingContext mappingContext) {
        Objects.requireNonNull(mappingContext, "窗口映射请求不能为 null");

        long fileSize = mappingContext.availableFileSize;
        int windowSize = mappingContext.windowSize;
        MappingDirection direction = mappingContext.mappingDirection;
        long baseOffset = mappingContext.windowBaseOffset;

        long adjustedWindowSize = computeAdjustedWindowSize(windowSize, fileSize);
        if (adjustedWindowSize == 0) {
            return new ArrayList<>();
        }

        long startOffset = direction.calculateStartOffset(baseOffset, adjustedWindowSize);
        long endOffset = direction.calculateEndOffset(baseOffset, adjustedWindowSize);

        return splitIntoSegments(startOffset, endOffset, fileSize);
    }

    // ==================== 内部静态辅助方法 ====================

    /**
     * 计算实际有效的窗口大小。
     * <p>
     * 规则：
     * <ul>
     *   <li>如果文件大小 ≤ 0，返回 0。</li>
     *   <li>如果期望窗口大小 ≤ 0 或大于文件大小，则窗口大小等于文件大小（映射整个文件）。</li>
     *   <li>否则返回期望窗口大小。</li>
     * </ul>
     *
     * @param windowSize 期望的窗口大小（字节，可能为 -1 或正数）
     * @param fileSize   文件总大小（字节，必须 ≥ 0）
     * @return 调整后的窗口大小（≥ 0）
     */
    private static long computeAdjustedWindowSize(long windowSize, long fileSize) {
        if (fileSize <= 0) {
            return 0;
        }
        if (windowSize <= 0 || windowSize > fileSize) {
            return fileSize;
        }
        return windowSize;
    }

    /**
     * 将起始和结束偏移量表示的窗口拆分为一个或多个窗口片段，
     * 自动处理文件边界环绕以及超出最大片段大小的情况。
     * <p>
     * 该算法会处理三种情况：
     * <ul>
     *   <li>起始偏移为负（窗口环绕到文件末尾）：将窗口拆分为 [fileSize+startOffset, fileSize-1] 和 [0, endOffset] 两部分。</li>
     *   <li>结束偏移超出文件大小（窗口环绕到文件开头）：拆分为 [startOffset, fileSize-1] 和 [0, endOffset-fileSize] 两部分。</li>
     *   <li>正常范围（无环绕）：直接在线性范围内拆分。</li>
     * </ul>
     * 对于每个连续的线性范围，如果其长度超过 {@link #MAX_SEGMENT_SIZE}，则会进一步拆分为多个片段。
     * </p>
     *
     * @param startOffset 窗口起始偏移（可能为负，表示回绕）
     * @param endOffset   窗口结束偏移（可能超出文件大小）
     * @param fileSize    文件总大小（字节）
     * @return 拆分后的窗口片段列表（按文件顺序排列），可能为空
     */
    private static List<Segment> splitIntoSegments(long startOffset, long endOffset, long fileSize) {
        List<Segment> segments = new ArrayList<>();

        // 起始偏移量为负（环绕到文件末尾）
        if (startOffset < 0) {
            segments.addAll(splitLinearRange(fileSize + startOffset, fileSize - 1));
            segments.addAll(splitLinearRange(0, endOffset));
            return segments;
        }

        // 结束偏移量超出文件大小（环绕到文件开头）
        if (endOffset >= fileSize) {
            segments.addAll(splitLinearRange(startOffset, fileSize - 1));
            segments.addAll(splitLinearRange(0, endOffset - fileSize));
            return segments;
        }

        // 正常范围（直接拆分）
        return splitLinearRange(startOffset, endOffset);
    }

    /**
     * 将一段连续的线性范围拆分为不超过 {@link #MAX_SEGMENT_SIZE} 的窗口片段列表。
     * <p>
     * 例如，如果范围长度为 2.5 * MAX_SEGMENT_SIZE，则拆分为 3 个片段：
     * 两个完整片段和一个剩余片段。
     * </p>
     *
     * @param startOffset 起始偏移（包含）
     * @param endOffset   结束偏移（包含）
     * @return 拆分后的窗口片段列表，始终至少包含一个片段（如果范围有效）；如果范围长度为 0，返回空列表
     */
    private static List<Segment> splitLinearRange(long startOffset, long endOffset) {
        List<Segment> segments = new ArrayList<>();
        long length = endOffset - startOffset + 1;

        if (length <= MAX_SEGMENT_SIZE) {
            segments.add(new Segment(startOffset, endOffset));
            return segments;
        }

        long fullSegments = length / MAX_SEGMENT_SIZE;
        long remainder = length % MAX_SEGMENT_SIZE;

        for (int i = 0; i < fullSegments; i++) {
            long segmentStart = startOffset + i * MAX_SEGMENT_SIZE;
            long segmentEnd = segmentStart + MAX_SEGMENT_SIZE - 1;
            segments.add(new Segment(segmentStart, segmentEnd));
        }

        if (remainder > 0) {
            long segmentStart = startOffset + fullSegments * MAX_SEGMENT_SIZE;
            segments.add(new Segment(segmentStart, endOffset));
        }

        return segments;
    }
}