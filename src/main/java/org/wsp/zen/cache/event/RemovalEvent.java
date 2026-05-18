package org.wsp.zen.cache.event;

import java.util.Objects;

/**
 * 缓存移除事件对象，封装了被移除缓存条目的键和值。
 * <p>
 * 当缓存中的条目因驱逐、显式删除、过期或替换等操作被移除时，
 * 会创建此事件对象并传递给 {@link org.wsp.zen.cache.core.RemovalListener}。
 * 该事件是不可变对象，且键和值均不能为 {@code null}。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>
 * RemovalListener&lt;String, byte[]&gt; listener = event -> {
 *     System.out.println("移除键: " + event.key);
 *     // 可以在这里清理资源
 * };
 * </pre>
 *
 * @param <K> 缓存键的类型
 * @param <V> 缓存值的类型
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.cache.core.RemovalListener
 */
public class RemovalEvent<K, V> {

    /**
     * 被移除缓存条目的键，永远不为 {@code null}
     */
    public final K key;

    /**
     * 被移除缓存条目的值，永远不为 {@code null}
     */
    public final V value;

    /**
     * 构造一个新的移除事件。
     *
     * @param key   被移除的缓存键，不能为 {@code null}
     * @param value 被移除的缓存值，不能为 {@code null}
     * @throws NullPointerException 如果 {@code key} 或 {@code value} 为 {@code null}
     */
    public RemovalEvent(K key, V value) {
        this.key = Objects.requireNonNull(key, "缓存键不能为 null");
        this.value = Objects.requireNonNull(value, "缓存值不能为 null");
    }
}