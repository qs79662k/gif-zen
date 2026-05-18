package org.wsp.zen.cache.core;

import java.io.Closeable;

/**
 * 线程安全的键值缓存接口，通过组合多个功能子接口提供完整的缓存操作能力。
 * <p>
 * 该接口整合了以下功能维度：
 * <ul>
 *   <li>{@link ComputableCache} — 支持延迟计算与原子性加载的缓存访问，避免缓存击穿</li>
 *   <li>{@link EvictableCache} — 支持基于策略的缓存条目淘汰，可在达到容量上限时自动回收</li>
 *   <li>{@link ListenableCache} — 支持注册缓存事件监听器（如条目添加、移除、过期），便于资源管理或监控</li>
 *   <li>{@link StatisticCache} — 提供缓存命中率、条目数量等统计信息，辅助运维分析与调优</li>
 *   <li>{@link Closeable} — 支持缓存的优雅关闭，释放底层存储或网络连接等资源</li>
 * </ul>
 * 所有组合的子接口契约均适用于本接口，实现类必须保证在多线程并发访问下的线程安全性。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Cache<Integer, String> cache = new DefaultCache<>();
 * cache.addRemovalListener(event -> {
 *     System.out.println("移除: " + event.key + " -> " + event.value);
 * });
 * String value = cache.fetchOrCompute(1, k -> "computed-" + k);
 * }</pre>
 *
 * @param <K> 缓存键的类型，建议实现良好的 {@code equals} 和 {@code hashCode} 方法
 * @param <V> 缓存值的类型
 * @author wsp
 * @version 1.0
 * @see ComputableCache
 * @see EvictableCache
 * @see ListenableCache
 * @see StatisticCache
 */
public interface Cache<K, V> extends
        ComputableCache<K, V>,
        EvictableCache<K, V>,
        ListenableCache<K, V>,
        StatisticCache<K, V>,
        Closeable { }