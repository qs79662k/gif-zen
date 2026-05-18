package org.wsp.zen.cache.core;

/**
 * 基础键值对缓存接口，定义缓存的通用操作。
 * <p>
 * 该接口抽象了缓存的增删查改及存在性判断等基本行为，不涉及并发控制、
 * 淘汰策略等高级特性。具体实现可根据需求添加容量限制、过期处理等功能。
 * </p>
 *
 * @param <K> 缓存键的类型
 * @param <V> 缓存值的类型
 * @author wsp
 * @version 1.0
 */
public interface BasicCache<K, V> {

    /**
     * 判断指定键是否已缓存。
     *
     * @param key 要检查的键，不能为 {@code null}
     * @return {@code true} 如果缓存中存在该键，否则 {@code false}
     */
    boolean isCached(K key);

    /**
     * 获取当前缓存中键值对的数量。
     *
     * @return 缓存条目数量，≥ 0
     */
    int size();

    /**
     * 根据键获取对应的缓存值。
     *
     * @param key 缓存键，不能为 {@code null}
     * @return 与键关联的值，若键不存在或已过期则返回 {@code null}
     */
    V get(K key);

    /**
     * 将键值对存入缓存。若键已存在，则替换旧值。
     *
     * @param key   缓存键，不能为 {@code null}
     * @param value 缓存值，可为 {@code null}（具体实现可决定是否允许 null 值）
     */
    void put(K key, V value);

    /**
     * 移除指定键对应的缓存条目。
     *
     * @param key 要移除的键，不能为 {@code null}
     * @return 被移除的旧值，若键不存在则返回 {@code null}
     */
    V remove(K key);

    /**
     * 清空缓存中的所有条目，重置缓存状态。
     */
    void clear();
}