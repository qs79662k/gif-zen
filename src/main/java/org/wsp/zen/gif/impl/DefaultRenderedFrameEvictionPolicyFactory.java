package org.wsp.zen.gif.impl;

import org.wsp.zen.cache.core.EvictionPolicy;
import org.wsp.zen.cache.impl.DefaultEvictionPolicy;
import org.wsp.zen.gif.core.EvictionPolicyFactory;
import org.wsp.zen.gif.model.EvictionPolicyContext;
import org.wsp.zen.gif.util.CacheWindowHelper;

import java.util.Objects;
import java.util.Set;

/**
 * 渲染结果缓存的默认驱逐策略工厂。
 * <p>
 * 该工厂根据给定的上下文动态生成一个 {@link EvictionPolicy}，用于决定在渲染结果缓存
 * 空间不足时哪些帧可以被驱逐。其核心保留规则委托给
 * {@link CacheWindowHelper#computeRenderKeepSet} 计算，综合考虑缓存窗口范围、
 * 播放方向、是否需要前一帧（RESTORE_PREVIOUS 模式）等因素。
 * </p>
 *
 * <p><b>保留规则：</b>
 * 保留集的计算由 {@code CacheWindowHelper.computeRenderKeepSet} 实现，大致包括：
 * <ul>
 *   <li>主路径或回绕路径范围内的帧（根据是否为正向播放动态调整）</li>
 *   <li>请求起始帧（保证用户当前可见帧不被驱逐）</li>
 *   <li>若当前帧需要恢复前一帧，则保留其前一帧</li>
 *   <li>反向播放时保留窗口内的部分帧</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该工厂是无状态的，因此是线程安全的。{@link #create(EvictionPolicyContext)} 方法
 * 可以被多个线程并发调用。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * EvictionPolicyFactory<Integer> factory = new DefaultRenderedFrameEvictionPolicyFactory();
 * EvictionPolicyContext context = new EvictionPolicyContext(...);
 * EvictionPolicy<Integer> policy = factory.create(context);
 *
 * // 与渲染结果缓存结合使用
 * renderFrameCache.evictEntries(policy);
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see EvictionPolicyFactory
 * @see DefaultEvictionPolicy
 * @see EvictionPolicyContext
 * @see CacheWindowHelper
 */
public class DefaultRenderedFrameEvictionPolicyFactory implements EvictionPolicyFactory<Integer> {

    /**
     * 根据给定的上下文创建驱逐策略实例。
     * <p>
     * 该方法从上下文中提取必要参数，委托 {@link CacheWindowHelper#computeRenderKeepSet}
     * 计算需要保留的帧索引集合，并包装为 {@link DefaultEvictionPolicy} 返回。
     * </p>
     *
     * @param context 驱逐策略上下文，包含当前帧索引、请求起始索引、缓存范围、窗口大小等信息，
     *                不能为 {@code null}
     * @return 新创建的驱逐策略实例，不为 {@code null}
     * @throws NullPointerException 如果 {@code context} 为 {@code null}
     */
    @Override
    public EvictionPolicy<Integer> create(EvictionPolicyContext context) {
        Objects.requireNonNull(context, "驱逐策略上下文对象不能为 null");

        int currentFrameIndex = context.currentFrameIndex;
        int requestStartIndex = context.requestStartIndex;
        int totalFrames = context.totalFrames;
        boolean isPrimary = context.isPrimaryPath;
        CacheWindowHelper.CacheRange fullRange = context.cacheRange;
        int backwardWindowSize = context.backwardWindowSize;
        boolean needPreviousFrame = context.needPreviousFrame;

        Set<Integer> renderKeep = CacheWindowHelper.computeRenderKeepSet(
                fullRange,
                currentFrameIndex,
                requestStartIndex,
                totalFrames,
                needPreviousFrame,
                isPrimary,
                backwardWindowSize
        );

        return new DefaultEvictionPolicy<>(renderKeep);
    }
}