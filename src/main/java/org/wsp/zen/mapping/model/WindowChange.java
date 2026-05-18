package org.wsp.zen.mapping.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 窗口片段变更描述，用于表示在滑动窗口过程中需要添加的新片段和需要保留的旧片段。
 * <p>
 * 该对象包含两个集合：
 * <ul>
 *   <li>{@link #segmentsToAdd}：需要新映射的窗口片段集合。</li>
 *   <li>{@link #segmentsToRetain}：当前已映射且需要继续保留的片段集合。</li>
 * </ul>
 * 通常与 {@link org.wsp.zen.mapping.core.MappingManager#applyChanges(WindowChange)} 配合使用，
 * 用于批量更新内存映射窗口，先释放不在保留集中的旧片段，再映射新片段。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的两个集合字段均为不可修改的视图（通过 {@link Collections#unmodifiableSet} 包装），
 * 因此实例是不可变的，可以安全地共享和传递。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see Segment
 * @see org.wsp.zen.mapping.core.MappingManager#applyChanges(WindowChange)
 */
public final class WindowChange {

    /** 需要新映射的窗口片段集合（不可变，不为 {@code null}） */
    public final Set<Segment> segmentsToAdd;

    /** 需要保留的窗口片段集合（不可变，不为 {@code null}） */
    public final Set<Segment> segmentsToRetain;

    /**
     * 构造一个窗口变更对象。
     *
     * @param segmentsToAdd   需要添加（新映射）的窗口片段集合，不能为 {@code null}（可以是空集合）
     * @param segmentsToRetain 需要保留的窗口片段集合，不能为 {@code null}（可以是空集合）
     * @throws NullPointerException 如果任一参数为 {@code null}
     */
    public WindowChange(Set<Segment> segmentsToAdd, Set<Segment> segmentsToRetain) {
        this.segmentsToAdd = Collections.unmodifiableSet(
                new HashSet<>(Objects.requireNonNull(segmentsToAdd, "需添加的片段集合不能为 null")));
        this.segmentsToRetain = Collections.unmodifiableSet(
                new HashSet<>(Objects.requireNonNull(segmentsToRetain, "需保留的片段集合不能为 null")));
    }
}