package org.wsp.zen.cache.impl;

import java.util.Objects;
import java.util.Set;

import org.wsp.zen.cache.core.EvictionPolicy;

/**
 * 默认的缓存驱逐策略实现，基于一个保留集（{@code keepSet}）决定键是否可被驱逐。
 * <p>
 * 此策略中，只有存在于保留集中的键会被视为“不可驱逐”（即保留），
 * 不在保留集中的键均可以被驱逐。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该策略本身不维护内部可变状态，线程安全性取决于传入的保留集 {@link Set} 实例。
 * 如果保留集在运行时被多线程修改，需使用线程安全集合或外部同步。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Set<Integer> importantFrames = Set.of(0, 5, 10);
 * EvictionPolicy<Integer> policy = new DefaultEvictionPolicy<>(importantFrames);
 *
 * // 键 0 在保留集中，不可驱逐
 * if (policy.isEvictable(0)) {
 *     // 不会执行
 * }
 *
 * // 键 100 不在保留集中，可驱逐
 * if (policy.isEvictable(100)) {
 *     // 执行驱逐操作
 * }
 * }</pre>
 *
 * @param <K> 缓存键的类型
 * @author wsp
 * @version 1.0
 * @see EvictionPolicy
 */
public class DefaultEvictionPolicy<K> implements EvictionPolicy<K> {

    /** 保留键集合，其中的键在驱逐时会被保留（不可驱逐） */
    private final Set<K> keepSet;

    /**
     * 构造一个基于保留集的驱逐策略。
     *
     * @param keepSet 需要保留（不可驱逐）的键集合，不能为 {@code null}。
     *                注意：传入的集合引用会被直接持有，后续对该集合内容的修改将实时影响驱逐判断结果。
     * @throws NullPointerException 如果 {@code keepSet} 为 {@code null}
     */
    public DefaultEvictionPolicy(Set<K> keepSet) {
        this.keepSet = Objects.requireNonNull(keepSet, "保留集不能为 null");
    }

    /**
     * 判断指定的键是否可以被驱逐。
     * <p>
     * 实现逻辑：如果键存在于保留集中，则返回 {@code false}（不可驱逐）；
     * 否则返回 {@code true}（可驱逐）。
     * </p>
     *
     * @param key 待检查的缓存键，不能为 {@code null}
     * @return {@code true} 如果键不在保留集中，即允许驱逐；{@code false} 如果键在保留集中
     * @throws NullPointerException 如果 {@code key} 为 {@code null}（由底层集合可能抛出）
     */
    @Override
    public boolean isEvictable(K key) {
        return !keepSet.contains(key);
    }
}