package org.wsp.zen.cache.core;

/**
 * 支持驱逐策略的键值缓存接口，继承自 {@link BasicCache}。
 * <p>
 * 通过 {@link #evictEntries(EvictionPolicy)} 方法结合指定的驱逐策略，
 * 实现缓存条目的批量清理，可用于容量控制、过期淘汰等场景。
 * </p>
 *
 * @param <K> 缓存键的类型
 * @param <V> 缓存值的类型
 * @author wsp
 * @version 1.0
 */
public interface EvictableCache<K, V> extends BasicCache<K, V> {

    /**
     * 根据给定的驱逐策略淘汰缓存中的条目。
     * <p>
     * 具体淘汰哪些条目由 {@code strategy} 决定，调用后可能部分或全部条目被移除。
     * 实现类应保证调用期间缓存状态的一致性和线程安全性。
     * </p>
     *
     * @param strategy 驱逐策略，用于选择需要淘汰的缓存键，不能为 {@code null}
     * @throws NullPointerException 如果 {@code strategy} 为 {@code null}
     */
    void evictEntries(EvictionPolicy<K> strategy);
}