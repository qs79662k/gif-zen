package org.wsp.zen.gif.core;

import org.wsp.zen.cache.core.EvictionPolicy;
import org.wsp.zen.gif.model.EvictionPolicyContext;

/**
 * 驱逐策略工厂接口，用于根据给定的上下文动态创建 {@link EvictionPolicy} 实例。
 * <p>
 * 该接口允许在运行时基于外部信息（如 GIF 解析状态、内存压力、配置参数等）灵活生成驱逐策略，
 * 典型应用场景包括：
 * <ul>
 *   <li>根据当前缓存占用率调整保留键集合</li>
 *   <li>根据 GIF 帧的访问模式（如关键帧）定制驱逐规则</li>
 *   <li>结合用户配置参数生成不同策略</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 工厂实现类应保证线程安全，因为 {@link #create(EvictionPolicyContext)} 方法可能被多个线程并发调用。
 * 通常，无状态工厂（不依赖可变成员）是天然线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * // 实现一个基于上下文配置的工厂
 * EvictionPolicyFactory<Integer> factory = context -> {
 *     Set<Integer> keepSet = new HashSet<>();
 *     // 根据上下文动态决定保留集，例如将 GIF 所有关键帧索引加入保留集
 *     if (context.getAttribute("keyFrames") instanceof Set) {
 *         keepSet.addAll((Set<Integer>) context.getAttribute("keyFrames"));
 *     }
 *     return new DefaultEvictionPolicy<>(keepSet);
 * };
 *
 * EvictionPolicyContext ctx = new EvictionPolicyContext();
 * ctx.setAttribute("keyFrames", Set.of(0, 5, 10));
 * EvictionPolicy<Integer> policy = factory.create(ctx);
 * }</pre>
 *
 * @param <K> 缓存键的类型，与驱逐策略的键类型一致
 * @author wsp
 * @version 1.0
 * @see EvictionPolicy
 * @see EvictionPolicyContext
 */
public interface EvictionPolicyFactory<K> {

    /**
     * 根据给定的上下文创建并返回一个新的驱逐策略实例。
     * <p>
     * 上下文对象通常包含用于决策的必要信息，例如缓存统计、帧元数据、用户配置等。
     * 实现类应对上下文中的属性进行类型检查和空值处理，避免因非法上下文导致运行时异常。
     * </p>
     *
     * @param context 用于创建驱逐策略的上下文，不能为 {@code null}
     * @return 新创建的驱逐策略实例，永远不为 {@code null}
     * @throws NullPointerException     如果 {@code context} 为 {@code null}
     * @throws IllegalArgumentException 如果上下文中缺少必要属性或属性值不合法
     */
    EvictionPolicy<K> create(EvictionPolicyContext context);
}