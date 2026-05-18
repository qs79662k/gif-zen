package org.wsp.zen.cache.core;

/**
 * 支持统计信息的键值缓存接口，继承自 {@link BasicCache}。
 * <p>
 * 该接口在基础缓存功能之外提供了查询缓存命中率的能力，有助于性能分析和调优。
 * 具体统计维度和实现细节（如命中/未命中计数）由实现类决定。
 * </p>
 *
 * @param <K> 缓存键的类型
 * @param <V> 缓存值的类型
 * @author wsp
 * @version 1.0
 */
public interface StatisticCache<K, V> extends BasicCache<K, V> {

    /**
     * 获取缓存的命中率。
     * <p>
     * 命中率定义为缓存命中次数与总访问次数的比值，取值范围通常为 {@code [0.0, 1.0]}。
     * 若缓存从未被访问，返回值可能为 {@code 0.0} 或 {@code Double.NaN}（取决于实现）。
     * </p>
     *
     * @return 当前缓存的命中率，取值范围一般介于 0.0 到 1.0 之间
     */
    default double getHitRate() {return 0;}
}