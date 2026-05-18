package org.wsp.zen.gif.util;

import org.wsp.zen.cache.core.EvictionPolicy;
import org.wsp.zen.gif.model.DisplayMode;
import org.wsp.zen.gif.model.FrameInfo;
import org.wsp.zen.cache.core.Cache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 缓存窗口计算辅助工具类，用于确定 GIF 渲染过程中需要缓存的帧索引范围。
 * <p>
 * 主要功能：
 * <ul>
 *   <li>根据播放方向（正向/反向）和窗口大小，计算主窗口和回绕窗口的缓存范围。</li>
 *   <li>找到距离目标帧最近且已缓存的可渲染关键帧（忽略 {@link DisplayMode#RESTORE_PREVIOUS} 模式）。</li>
 *   <li>生成渲染后应保留的帧索引集合，供缓存淘汰策略使用。</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该类所有方法均为静态且无副作用（不修改外部状态），因此是线程安全的。
 * 可以在多线程环境中安全调用，无需额外同步。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * CacheWindowHelper.CacheRange range = CacheWindowHelper.computeRenderCacheRange(
 *         10, true, totalFrames, frameInfos, renderFrameCache, 5, 3);
 * Set<Integer> keepSet = CacheWindowHelper.computeRenderKeepSet(
 *         range, currentFrameIndex, requestStartIndex, totalFrames, needPreviousFrame, isPrimary, backwardWindowSize);
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see EvictionPolicy#computeAdjustedWindowSize(int, int)
 * @see FrameInfo
 * @see DisplayMode
 */
public final class CacheWindowHelper {

    // 工具类，禁止实例化
    private CacheWindowHelper() {}

    // ==================== 公开 API ====================

    /**
     * 根据播放方向计算需要缓存的帧索引范围（主窗口 + 可能的回绕窗口）。
     *
     * @param currentStartIndex   当前起始帧索引
     * @param isForwardDirection  播放方向（true 正向，false 反向）
     * @param totalFrames         总帧数
     * @param frameInfos          帧元数据列表
     * @param renderFrameCache    渲染帧缓存
     * @param forwardWindowSize   正向窗口大小
     * @param backwardWindowSize  反向窗口大小
     * @param <T>                 渲染结果类型
     * @return 缓存范围对象，包含主窗口和回绕窗口
     */
    public static <T> CacheRange computeRenderCacheRange(int currentStartIndex,
                                                         boolean isForwardDirection,
                                                         int totalFrames,
                                                         List<FrameInfo> frameInfos,
                                                         Cache<Integer, T> renderFrameCache,
                                                         int forwardWindowSize,
                                                         int backwardWindowSize) {
        if (isForwardDirection) {
            return computeForwardRenderCacheRange(currentStartIndex, totalFrames, frameInfos,
                    renderFrameCache, forwardWindowSize);
        } else {
            return computeBackwardRenderCacheRange(currentStartIndex, totalFrames, frameInfos,
                    renderFrameCache, backwardWindowSize);
        }
    }

    /**
     * 查找距离目标帧最近且已缓存的可渲染关键帧。
     *
     * @param targetIndex       目标帧索引
     * @param frameInfos        帧元数据列表
     * @param renderFrameCache  渲染帧缓存
     * @param <T>               渲染结果类型
     * @return 找到的最近可渲染关键帧索引，若未找到则返回默认关键帧索引
     */
    public static <T> int findRenderKeyframe(int targetIndex,
                                             List<FrameInfo> frameInfos,
                                             Cache<Integer, T> renderFrameCache) {
        FrameInfo frameInfo = frameInfos.get(targetIndex);
        int defaultKeyframeIndex = frameInfo.getKeyframeIndex();

        for (int i = targetIndex; i >= defaultKeyframeIndex; i--) {
            DisplayMode displayMode = frameInfos.get(i).getDisplayMode();
            if (displayMode != DisplayMode.RESTORE_PREVIOUS) {
                if (isFrameRendered(renderFrameCache, i)) {
                    return i;
                }
            }
        }
        return defaultKeyframeIndex;
    }

    /**
     * 计算渲染后应保留在缓存中的帧索引集合。
     *
     * @param range              缓存范围
     * @param currentFrameIndex  当前正在渲染的帧索引
     * @param requestStartIndex  请求的起始帧索引
     * @param totalFrames        总帧数
     * @param needPreviousFrame  是否需要保留前一帧
     * @param isPrimary          是否为主路径
     * @param backwardWindowSize 反向窗口大小
     * @return 应保留的帧索引集合
     */
    public static Set<Integer> computeRenderKeepSet(CacheRange range,
                                                    int currentFrameIndex,
                                                    int requestStartIndex,
                                                    int totalFrames,
                                                    boolean needPreviousFrame,
                                                    boolean isPrimary,
                                                    int backwardWindowSize) {
        Set<Integer> keep = new HashSet<>();
        PartialRange primaryRange = range.primaryRange;
        PartialRange wraparoundRange = range.wraparoundRange;
        boolean isForward = range.isForwardDirection;

        if (isForward) {
            handleForwardDirection(keep, primaryRange, wraparoundRange, currentFrameIndex, requestStartIndex,
                    totalFrames, needPreviousFrame, isPrimary);
        } else {
            handleBackwardDirection(keep, primaryRange, wraparoundRange, currentFrameIndex, requestStartIndex,
                    totalFrames, needPreviousFrame, isPrimary, backwardWindowSize);
        }
        return keep;
    }

    /**
     * 将一个 PartialRange 中的所有帧索引添加到集合中。
     */
    public static void addRangeToSet(Set<Integer> set, PartialRange range) {
        if (range == null || range.isEmpty()) {
            return;
        }
        int start = range.startIndex;
        int end = range.getEndIndex();
        for (int i = start; i <= end; i++) {
            set.add(i);
        }
    }

    // ==================== 私有辅助方法：方向处理 ====================

    private static void handleForwardDirection(Set<Integer> keep,
                                               PartialRange primaryRange,
                                               PartialRange wraparoundRange,
                                               int currentFrameIndex,
                                               int requestStartIndex,
                                               int totalFrames,
                                               boolean needPreviousFrame,
                                               boolean isPrimary) {
        if (isPrimary) {
            int mainEnd = primaryRange.getEndIndex();
            int from;
            if (currentFrameIndex <= requestStartIndex) {
                from = needPreviousFrame ? (currentFrameIndex - 1) : currentFrameIndex;
            } else {
                from = requestStartIndex;
            }
            addCyclicRange(keep, from, mainEnd, totalFrames);
            if (!wraparoundRange.isEmpty()) {
                addRangeToSet(keep, wraparoundRange);
            }
        } else {
            addRangeToSet(keep, primaryRange);
            if (!wraparoundRange.isEmpty()) {
                addRangeToSet(keep, wraparoundRange);
            }
        }
    }

    private static void handleBackwardDirection(Set<Integer> keep,
                                                PartialRange primaryRange,
                                                PartialRange wraparoundRange,
                                                int currentFrameIndex,
                                                int requestStartIndex,
                                                int totalFrames,
                                                boolean needPreviousFrame,
                                                boolean isPrimary,
                                                int backwardWindowSize) {
        if (isPrimary) {
            int distance = requestStartIndex - currentFrameIndex + 1;
            int from;
            if (distance >= backwardWindowSize) {
                from = needPreviousFrame ? (currentFrameIndex - 1) : currentFrameIndex;
            } else {
                from = requestStartIndex - backwardWindowSize + 1;
            }
            addCyclicRange(keep, from, requestStartIndex, totalFrames);
            if (!wraparoundRange.isEmpty()) {
                addRangeToSet(keep, wraparoundRange);
            }
        } else {
            addRangeToSet(keep, primaryRange);
            if (!wraparoundRange.isEmpty()) {
                int wrapTo = wraparoundRange.getEndIndex();
                int requiredWrapLength = backwardWindowSize - primaryRange.frameCount;
                requiredWrapLength = Math.max(requiredWrapLength, 0);
                int wrapKeepStart = wrapTo - requiredWrapLength + 1;
                int finalFrom = Math.min(currentFrameIndex, wrapKeepStart);
                addCyclicRange(keep, finalFrom, wrapTo, totalFrames);
            }
        }
    }

    // ==================== 私有辅助方法：窗口计算 ====================

    private static <T> CacheRange computeForwardRenderCacheRange(int currentStartIndex,
                                                                 int totalFrames,
                                                                 List<FrameInfo> frameInfos,
                                                                 Cache<Integer, T> renderFrameCache,
                                                                 int forwardWindowSize) {
        int windowSize = EvictionPolicy.computeAdjustedWindowSize(totalFrames, forwardWindowSize);
        int desiredEndIndex = currentStartIndex + windowSize - 1;
        int primaryEndIndex = Math.min(totalFrames - 1, desiredEndIndex);
        int primaryKeyframeIndex = findRenderKeyframe(currentStartIndex, frameInfos, renderFrameCache);
        int primaryFrameCount = primaryEndIndex - primaryKeyframeIndex + 1;
        PartialRange primaryRange = new PartialRange(primaryKeyframeIndex, primaryFrameCount);

        PartialRange wraparoundRange = PartialRange.EMPTY;
        if (desiredEndIndex >= totalFrames) {
            int wraparoundFrameCount = Math.min(primaryKeyframeIndex, desiredEndIndex - totalFrames + 1);
            if (wraparoundFrameCount > 0) {
                wraparoundRange = new PartialRange(0, wraparoundFrameCount);
            }
        }
        return new CacheRange(primaryRange, wraparoundRange, true);
    }

    private static <T> CacheRange computeBackwardRenderCacheRange(int currentStartIndex,
                                                                  int totalFrames,
                                                                  List<FrameInfo> frameInfos,
                                                                  Cache<Integer, T> renderFrameCache,
                                                                  int backwardWindowSize) {
        int windowSize = EvictionPolicy.computeAdjustedWindowSize(totalFrames, backwardWindowSize);
        int desiredStartIndex = currentStartIndex - windowSize + 1;
        int primaryStartIndex = Math.max(0, desiredStartIndex);
        int primaryKeyframeIndex = findRenderKeyframe(primaryStartIndex, frameInfos, renderFrameCache);
        int primaryFrameCount = currentStartIndex - primaryKeyframeIndex + 1;
        PartialRange primaryRange = new PartialRange(primaryKeyframeIndex, primaryFrameCount);

        PartialRange wraparoundRange = PartialRange.EMPTY;
        if (desiredStartIndex < 0) {
            int desiredWraparoundCount = Math.min(totalFrames - currentStartIndex - 1, -desiredStartIndex);
            int wraparoundStartIndex = totalFrames - desiredWraparoundCount;
            if (wraparoundStartIndex < totalFrames) {
                int wraparoundKeyframeIndex = findRenderKeyframe(wraparoundStartIndex, frameInfos, renderFrameCache);
                int actualWraparoundCount = totalFrames - wraparoundKeyframeIndex;
                wraparoundRange = new PartialRange(wraparoundKeyframeIndex, actualWraparoundCount);
            }
        }
        return new CacheRange(primaryRange, wraparoundRange, false);
    }

    // ==================== 私有辅助方法：工具 ====================

    private static <T> boolean isFrameRendered(Cache<Integer, T> renderFrameCache, int frameIndex) {
        T cachedFrame = renderFrameCache.fetchOrCompute(frameIndex, (index) -> null);
        return cachedFrame != null;
    }

    private static void addCyclicRange(Set<Integer> set, int from, int to, int totalFrames) {
        int normFrom = Math.floorMod(from, totalFrames);
        int normTo = Math.floorMod(to, totalFrames);
        if (normFrom <= normTo) {
            for (int i = normFrom; i <= normTo; i++) {
                set.add(i);
            }
        } else {
            for (int i = normFrom; i < totalFrames; i++) {
                set.add(i);
            }
            for (int i = 0; i <= normTo; i++) {
                set.add(i);
            }
        }
    }

    // ==================== 内部辅助类 ====================

    /**
     * 表示一个局部的帧索引范围（从 startIndex 开始，共 frameCount 帧）。
     */
    public static class PartialRange {
        public final int startIndex;
        public final int frameCount;
        public static final PartialRange EMPTY = new PartialRange(0, 0);

        public PartialRange(int startIndex, int frameCount) {
            if (startIndex < 0) {
                throw new IllegalArgumentException("缓存范围开始帧索引不能为负数: " + startIndex);
            }
            if (frameCount < 0) {
                throw new IllegalArgumentException("缓存范围帧数不能为负数: " + frameCount);
            }
            this.startIndex = startIndex;
            this.frameCount = frameCount;
        }

        public boolean isEmpty() {
            return frameCount <= 0;
        }

        public int getEndIndex() {
            return startIndex + frameCount - 1;
        }
    }

    /**
     * 封装主窗口和回绕窗口的缓存范围，以及播放方向。
     */
    public static class CacheRange {
        public final PartialRange primaryRange;
        public final PartialRange wraparoundRange;
        public final boolean isForwardDirection;

        public CacheRange(PartialRange primaryRange, PartialRange wraparoundRange, boolean isForwardDirection) {
            this.primaryRange = primaryRange;
            this.wraparoundRange = wraparoundRange;
            this.isForwardDirection = isForwardDirection;
        }
    }
}