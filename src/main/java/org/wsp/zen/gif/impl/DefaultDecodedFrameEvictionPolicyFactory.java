package org.wsp.zen.gif.impl;

import org.wsp.zen.cache.core.EvictionPolicy;
import org.wsp.zen.cache.impl.DefaultEvictionPolicy;
import org.wsp.zen.gif.core.EvictionPolicyFactory;
import org.wsp.zen.gif.model.EvictionPolicyContext;
import org.wsp.zen.gif.util.CacheWindowHelper;
import org.wsp.zen.gif.util.CacheWindowHelper.CacheRange;
import org.wsp.zen.gif.util.CacheWindowHelper.PartialRange;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 解码帧缓存的默认驱逐策略工厂。
 * <p>
 * 该工厂根据给定的上下文动态生成一个 {@link EvictionPolicy}，用于决定在帧缓存空间不足时
 * 哪些帧可以被驱逐。其核心保留规则基于双缓存窗口（Primary / Wraparound）机制，
 * 旨在最大化缓存命中率，减少重复解码。
 * </p>
 *
 * <p><b>保留规则：</b>
 * <ol>
 *   <li>另一路径的所有帧（若当前为主路径，则保留回绕路径的所有帧；反之亦然）</li>
 *   <li>当前帧索引 +1（即下一个预取解码的帧索引）</li>
 *   <li>正向播放时，另一路径边界之后的第一帧（用于预加载连续性）</li>
 * </ol>
 * 不在保留集内的帧将被视为可驱逐。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该工厂是无状态的，因此是线程安全的。{@link #create(EvictionPolicyContext)} 方法
 * 可以被多个线程并发调用。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * EvictionPolicyFactory<Integer> factory = new DefaultDecodedFrameEvictionPolicyFactory();
 * EvictionPolicyContext context = new EvictionPolicyContext.Builder()
 *         .currentFrameIndex(5)
 *         .totalFrames(30)
 *         .isForwardDirection(true)
 *         .cacheRange(new CacheRange(...))
 *         .isPrimaryPath(true)
 *         .build();
 * EvictionPolicy<Integer> policy = factory.create(context);
 *
 * // 后续与缓存管理器结合使用
 * cacheManager.evictCache("decodedFrames", policy, Integer.class);
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see EvictionPolicyFactory
 * @see DefaultEvictionPolicy
 * @see EvictionPolicyContext
 * @see CacheWindowHelper
 */
public class DefaultDecodedFrameEvictionPolicyFactory implements EvictionPolicyFactory<Integer> {

    /**
     * 根据给定的上下文创建驱逐策略实例。
     * <p>
     * 该方法会根据上下文中的播放方向、缓存范围以及路径标识，计算出需要保留的帧索引集合，
     * 并包装为 {@link DefaultEvictionPolicy} 返回。
     * </p>
     *
     * @param context 驱逐策略上下文，包含当前帧索引、总帧数、播放方向、缓存范围等信息，
     *                不能为 {@code null}
     * @return 新创建的驱逐策略实例，不为 {@code null}
     * @throws NullPointerException 如果 {@code context} 为 {@code null}
     */
    @Override
    public EvictionPolicy<Integer> create(EvictionPolicyContext context) {
        Objects.requireNonNull(context, "驱逐策略上下文对象不能为 null");

        int currentFrameIndex = context.currentFrameIndex;
        int totalFrames = context.totalFrames;
        boolean isForwardDirection = context.isForwardDirection();
        CacheRange fullRange = context.cacheRange;
        boolean isPrimary = context.isPrimaryPath;

        // 预取解码索引：当前帧的下一帧（循环取模）
        int decodeIdx = (currentFrameIndex + 1) % totalFrames;

        // 获取另一路径的范围（若为主路径则取回绕范围，否则取主路径范围）
        PartialRange otherRange = isPrimary ? fullRange.wraparoundRange : fullRange.primaryRange;

        // 将另一路径范围内的所有帧索引加入保留集
        Set<Integer> otherBase = new HashSet<>();
        if (otherRange != null && !otherRange.isEmpty()) {
            CacheWindowHelper.addRangeToSet(otherBase, otherRange);
        }

        // 正向播放时，计算另一路径边界之后的下一帧索引（用于预加载连续性）
        int next1 = -1;
        if (isForwardDirection && otherRange != null && !otherRange.isEmpty()) {
            next1 = (otherRange.startIndex + otherRange.frameCount) % totalFrames;
        }

        // 构建最终保留集：另一路径全部帧 + 预取帧 + 边界后续帧
        Set<Integer> decodeKeep = new HashSet<>(otherBase);
        decodeKeep.add(decodeIdx);
        if (next1 != -1) {
            decodeKeep.add(next1);
        }

        return new DefaultEvictionPolicy<>(decodeKeep);
    }
}