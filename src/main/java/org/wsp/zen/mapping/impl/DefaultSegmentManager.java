package org.wsp.zen.mapping.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.wsp.zen.gif.util.CloseState;
import org.wsp.zen.mapping.core.SegmentManager;
import org.wsp.zen.mapping.core.WindowPolicy;
import org.wsp.zen.mapping.model.CompositeReadOperation;
import org.wsp.zen.mapping.model.ContiguousRange;
import org.wsp.zen.mapping.model.MappingContext;
import org.wsp.zen.mapping.model.Segment;
import org.wsp.zen.mapping.model.SegmentReadOperation;
import org.wsp.zen.mapping.model.WindowChange;
import org.wsp.zen.mapping.util.MappingValidateUtils;

/**
 * 窗口片段管理器的默认实现，负责维护当前已映射的窗口片段集合，并提供基于连续范围的查询与变更功能。
 * <p>
 * <b>核心设计说明：</b><br>
 * 片段保留采用“旧片段需完整包含新片段才保留”的策略，而非简单的“重叠即保留”。
 * 若采用重叠保留，可能导致大量碎片映射，增加管理复杂度；而采用完整包含保留，能有效减少片段数量，
 * 降低管理成本，提升整体性能。例如：
 * <ul>
 *   <li>旧片段：100-200</li>
 *   <li>新片段：50-300</li>
 *   <li>重叠保留会产生两个新片段（50-99、201-300）</li>
 *   <li>完整包含保留则仅需保留新片段（50-300）</li>
 * </ul>
 * </p>
 *
 * <p><b>内部结构：</b>
 * <ul>
 *   <li>{@code segments}：当前已映射的窗口片段集合，按起始偏移升序排列。</li>
 *   <li>{@code contiguousRanges}：由当前片段集合计算出的连续范围列表（相邻且无间隙的片段合并而成）。</li>
 * </ul>
 * 每次更新窗口片段后，会重新计算连续范围，以加速后续的读取范围查询。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 本类<strong>不是线程安全的</strong>。多线程环境下的安全使用必须由上层调用者负责同步。
 * 具体约束如下：
 * <ul>
 *   <li><b>修改操作</b>（如 {@link #updateAndGetChanges(MappingContext)}、
 *       {@link #removeSegment(Segment)}、{@link #clear()}、{@link #close()}）：
 *       必须由外部进行互斥同步，避免并发修改导致内部集合状态不一致。</li>
 *   <li><b>只读操作</b>（如 {@link #get(long, int)}、{@link #count()}、{@link #isEmpty()}）：
 *       如果与写操作并发，仍需要外部同步以保证读取到一致的状态；若确定在读取期间没有写操作，
 *       则可以不加锁（但通常推荐使用读写锁）。</li>
 *   <li>推荐的上层同步策略：使用 {@code ReentrantReadWriteLock}，读操作获取读锁，写操作获取写锁。
 *       例如 {@link DefaultFileMappingManager} 中的做法。</li>
 * </ul>
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see SegmentManager
 * @see WindowPolicy
 */
public class DefaultSegmentManager implements SegmentManager {

    /** 窗口计算策略，用于生成目标窗口片段列表 */
    private final WindowPolicy policy;

    /** 当前已映射的窗口片段集合，按起始偏移升序排列（非线程安全，需外部同步） */
    private SortedSet<Segment> segments = new TreeSet<>();

    /** 由当前片段集合计算出的连续范围列表（无间隙片段合并而成，非线程安全，需外部同步） */
    private List<ContiguousRange> contiguousRanges = new ArrayList<>();

    /** 生命周期状态管理器 */
    private final CloseState closeState = new CloseState("窗口片段管理器");

    // ==================== 构造器 ====================

    /**
     * 使用默认的窗口计算策略（{@link DefaultWindowPolicy}）创建管理器。
     * <p>
     * <b>线程安全：</b>构造器本身安全，但构造出的实例非线程安全。
     * </p>
     */
    public DefaultSegmentManager() {
        this(new DefaultWindowPolicy());
    }

    /**
     * 使用指定的窗口计算策略创建管理器。
     *
     * @param policy 窗口计算策略，不能为 {@code null}
     * @throws NullPointerException 如果 {@code policy} 为 {@code null}
     * <p>
     * <b>线程安全：</b>构造器本身安全，但构造出的实例非线程安全。
     * </p>
     */
    public DefaultSegmentManager(WindowPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "窗口策略对象不能为 null");
    }

    // ==================== 窗口更新与变更计算 ====================

    /**
     * 根据映射请求更新当前窗口片段，并返回本次变更（新增与保留的片段）。
     * <p>
     * 该方法首先通过计算策略获得新的目标片段列表，然后与当前片段比较，
     * 确定哪些旧片段需要保留（与新增片段重叠且被包含在同一连续范围内），
     * 哪些新片段需要添加（未被任何现有连续范围包含）。
     * 随后更新内部片段集合和连续范围缓存。
     * </p>
     * <p>
     * <b>线程安全：</b>该方法会修改内部状态，<strong>必须在外部写锁保护下调用</strong>，
     * 禁止与任何其他读操作或写操作并发执行。
     * </p>
     *
     * @param mappingContext 窗口映射请求，包含目标窗口的偏移信息，不能为 {@code null}
     * @return 窗口变更对象，包含需保留的旧片段和需添加的新片段，不为 {@code null}
     * @throws NullPointerException     如果 {@code mappingContext} 为 {@code null}
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public WindowChange updateAndGetChanges(MappingContext mappingContext) {
        closeState.checkClosed();
        List<Segment> newSegments = policy.calculateWindowFragments(mappingContext);

        WindowChange windowChange = computeChanges(newSegments);

        // 先移除不再需要的旧片段，再添加新增的片段
        segments.removeIf(segment -> !windowChange.segmentsToRetain.contains(segment));
        segments.addAll(windowChange.segmentsToAdd);

        // 仅在片段有变化时重新计算连续范围
        if (!windowChange.segmentsToAdd.isEmpty() || segments.size() != windowChange.segmentsToRetain.size()) {
            contiguousRanges = findContiguousRanges();
        }

        System.out.println("窗口片段更新: 保留 " + windowChange.segmentsToRetain.size() +
                " 个, 新增 " + windowChange.segmentsToAdd.size() + " 个");

        return windowChange;
    }

    /**
     * 计算当前片段与目标新片段之间的差异，确定需要添加和保留的片段。
     * <p>
     * 保留策略：如果一个新片段被某个现有的连续范围完全包含，则将该连续范围内
     * 与新片段有重叠的旧片段标记为保留；否则，该新片段标记为需要添加。
     * </p>
     *
     * @param newSegments 新计算出的目标片段列表，不能为 {@code null}
     * @return 窗口变更对象，包含需添加和需保留的片段集合
     */
    private WindowChange computeChanges(List<Segment> newSegments) {
        Set<Segment> segmentsToAdd = new HashSet<>();
        Set<Segment> segmentsToRetain = new HashSet<>();

        if (segments.isEmpty()) {
            segmentsToAdd.addAll(newSegments);
            return new WindowChange(segmentsToAdd, segmentsToRetain);
        }

        if (newSegments.isEmpty()) {
            return new WindowChange(segmentsToAdd, segmentsToRetain);
        }

        for (Segment newSegment : newSegments) {
            boolean isContained = false;
            for (ContiguousRange contiguousRange : contiguousRanges) {
                if (contiguousRange.isContained(newSegment)) {
                    isContained = true;
                    for (Segment oldSegment : contiguousRange.segments) {
                        if (newSegment.isOverlapping(oldSegment)) {
                            segmentsToRetain.add(oldSegment);
                            if (oldSegment.endOffset >= newSegment.endOffset) {
                                break; // 已到达与 newSegment 重叠的末尾片段
                            }
                        }
                    }
                    break;
                }
            }
            if (!isContained) {
                segmentsToAdd.add(newSegment);
            }
        }

        return new WindowChange(segmentsToAdd, segmentsToRetain);
    }

    // ==================== 读取范围查询 ====================

    /**
     * 根据起始偏移量和长度，获取对应的复合读取操作。
     * <p>
     * 该方法仅处理被某个连续范围完全包含的读取请求，通过有序遍历和提前终止优化性能。
     * 如果请求的范围不能被任何现有的连续范围完全覆盖，则返回 {@code null}。
     * </p>
     * <p>
     * <b>线程安全：</b>该方法为只读操作，但为了看到一致的内部状态（如连续范围列表），
     * 建议在外部读锁保护下调用。如果在读取期间确定不会有写操作并发，也可以不加锁，
     * 但需自行承担可见性问题（例如使用 volatile 或合适的发布机制）。
     * </p>
     *
     * @param startOffset 读取范围的起始偏移量（≥ 0）
     * @param length      读取的长度（> 0）
     * @return 复合读取操作对象，包含所有重叠片段的读取信息；如果范围不被任何连续范围完全包含，返回 {@code null}
     * @throws IllegalArgumentException 如果起始偏移量为负数或长度 ≤ 0
     * @throws IllegalStateException    如果管理器已关闭或连续范围列表未初始化
     */
    @Override
    public CompositeReadOperation get(long startOffset, int length) {
        closeState.checkClosed();

        if (contiguousRanges == null) {
            throw new IllegalStateException("连续范围列表未初始化");
        }

        MappingValidateUtils.validateRangeArguments(startOffset, length);

        List<SegmentReadOperation> segmentReads = new ArrayList<>();
        int currentPosition = 0;
        long endOffset = startOffset + length - 1;

        for (ContiguousRange contiguousRange : contiguousRanges) {
            // 只有当前连续范围完全包含读取范围时才处理
            if (contiguousRange.isContained(startOffset, endOffset)) {
                for (Segment segment : contiguousRange.segments) {
                    if (segment.isOverlapping(startOffset, endOffset)) {
                        int currentStartOffset = (int) (Math.max(startOffset, segment.startOffset) - segment.startOffset);
                        int currentEndOffset = (int) (Math.min(endOffset, segment.endOffset) - segment.startOffset);

                        segmentReads.add(new SegmentReadOperation(
                                segment, currentStartOffset, currentEndOffset, currentPosition));

                        currentPosition += (currentEndOffset - currentStartOffset + 1);

                        if (segment.endOffset >= endOffset) {
                            return new CompositeReadOperation(segmentReads, length);
                        }
                    }
                }
            }
        }

        System.err.println("范围 [" + startOffset + ", " + endOffset + "] 不被连续片段完全包含");
        return null;
    }

    // ==================== 连续范围计算 ====================

    /**
     * 从当前片段集合中找出所有连续的片段范围（相邻且无间隙的片段合并为连续范围）。
     * <p>
     * 连续的定义：前一片段的 {@code endOffset + 1 == 后一片段的 startOffset}。
     * </p>
     *
     * @return 连续范围列表，按起始偏移升序排列
     */
    private List<ContiguousRange> findContiguousRanges() {
        List<ContiguousRange> continuousRanges = new ArrayList<>();

        if (segments.isEmpty()) {
            return continuousRanges;
        }

        Iterator<Segment> iterator = segments.iterator();
        Segment firstSegment = iterator.next();
        long startOffset = firstSegment.startOffset;
        long endOffset = firstSegment.endOffset;
        SortedSet<Segment> currentSegments = new TreeSet<>();
        currentSegments.add(firstSegment);

        while (iterator.hasNext()) {
            Segment currentSegment = iterator.next();
            if (endOffset + 1 != currentSegment.startOffset) {
                // 出现间隙，保存当前连续范围
                continuousRanges.add(new ContiguousRange(startOffset, endOffset, currentSegments));
                currentSegments = new TreeSet<>();
                startOffset = currentSegment.startOffset;
            }
            endOffset = currentSegment.endOffset;
            currentSegments.add(currentSegment);
        }

        continuousRanges.add(new ContiguousRange(startOffset, endOffset, currentSegments));
        return continuousRanges;
    }

    // ==================== 状态查询 ====================

    /**
     * 返回当前管理的窗口片段数量。
     * <p>
     * <b>线程安全：</b>只读操作，但为了看到一致的值，建议在外部读锁保护下调用。
     * </p>
     *
     * @return 片段数量（≥ 0）
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public int count() {
        closeState.checkClosed();
        return segments.size();
    }

    /**
     * 判断当前管理的窗口片段集合是否为空。
     * <p>
     * <b>线程安全：</b>只读操作，但为了看到一致的值，建议在外部读锁保护下调用。
     * </p>
     *
     * @return {@code true} 如果没有管理任何片段，否则 {@code false}
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public boolean isEmpty() {
        closeState.checkClosed();
        return segments.isEmpty();
    }

    // ==================== 片段移除与清理 ====================

    /**
     * 移除指定的窗口片段，并更新连续范围缓存。
     * <p>
     * <b>线程安全：</b>该方法会修改内部状态，<strong>必须在外部写锁保护下调用</strong>，
     * 禁止与其他读写操作并发。
     * </p>
     *
     * @param segment 需要移除的窗口片段，不能为 {@code null}
     * @return {@code true} 如果片段存在并被成功移除，否则 {@code false}
     * @throws NullPointerException 如果 {@code segment} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public boolean removeSegment(Segment segment) {
        closeState.checkClosed();
        Objects.requireNonNull(segment, "需移除的窗口片段不能为 null");

        boolean removed = segments.remove(segment);
        if (removed) {
            contiguousRanges = findContiguousRanges();
            System.out.println("已成功移除窗口片段：" + segment);
        }
        return removed;
    }

    /**
     * 清空当前管理的所有窗口片段，并清空连续范围列表。
     * <p>
     * <b>线程安全：</b>该方法会修改内部状态，<strong>必须在外部写锁保护下调用</strong>。
     * </p>
     *
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public void clear() {
        closeState.checkClosed();
        segments.clear();
        contiguousRanges.clear();
    }

    // ==================== 生命周期管理 ====================

    /**
     * 关闭窗口片段管理器，释放所有内部资源。
     * <p>
     * 关闭后，管理器不可再使用。任何后续调用都会抛出 {@link IllegalStateException}。
     * </p>
     * <p>
     * <b>线程安全：</b>该方法会修改内部状态，<strong>必须在外部写锁保护下调用</strong>，
     * 并且应确保关闭后没有其他线程继续访问本实例。
     * </p>
     *
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public void close() {
        closeState.checkClosed();
        clear();
        closeState.markAsClosed();
    }
}