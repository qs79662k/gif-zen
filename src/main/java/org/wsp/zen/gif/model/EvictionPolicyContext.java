package org.wsp.zen.gif.model;

import java.util.Objects;

import org.wsp.zen.gif.util.CacheWindowHelper.CacheRange;

/**
 * 缓存驱逐策略的上下文对象，包含渲染一帧后可用于驱逐决策的所有必要信息。
 * <p>
 * 该类不可变，所有字段均为 public final，便于直接访问。
 * 构造时会进行严格的参数校验，确保传入的数据有效。
 * </p>
 *
 * @author wsp
 * @version 1.0
 */
public final class EvictionPolicyContext {

    /** 当前刚完成渲染的帧索引（0-based） */
    public final int currentFrameIndex;

    /** 当前路径是否为主路径（true）还是回绕路径（false） */
    public final boolean isPrimaryPath;

    /** 本次渲染覆盖的完整缓存窗口范围，包含主窗口和回绕窗口 */
    public final CacheRange cacheRange;

    /** 触发本次渲染流水线的原始请求帧索引 */
    public final int requestStartIndex;

    /** 正向播放时的窗口大小（帧数） */
    public final int forwardWindowSize;

    /** 反向播放时的窗口大小（帧数） */
    public final int backwardWindowSize;

    /** GIF 当前已知的总帧数 */
    public final int totalFrames;

    /** 当前帧的显示模式是否为 RESTORE_PREVIOUS（需要保留前一帧） */
    public final boolean needPreviousFrame;

    /**
     * 构造一个驱逐策略上下文对象。
     *
     * @param currentFrameIndex  当前渲染完成的帧索引，必须 ≥ 0
     * @param isPrimaryPath      是否为主路径
     * @param cacheRange         缓存窗口范围，不能为 {@code null}
     * @param requestStartIndex  原始请求帧索引，必须 ≥ 0
     * @param forwardWindowSize  正向窗口大小，必须 ≥ 0
     * @param backwardWindowSize 反向窗口大小，必须 ≥ 0
     * @param totalFrames        总帧数，必须 ＞ 0
     * @param needPreviousFrame  当前帧是否需要保留前一帧（RESTORE_PREVIOUS 模式）
     * @throws NullPointerException     如果 {@code cacheRange} 为 {@code null}
     * @throws IllegalArgumentException 如果任一整数参数超出合法范围
     */
    public EvictionPolicyContext(int currentFrameIndex,
                                   boolean isPrimaryPath,
                                   CacheRange cacheRange,
                                   int requestStartIndex,
                                   int forwardWindowSize,
                                   int backwardWindowSize,
                                   int totalFrames,
                                   boolean needPreviousFrame) {
        Objects.requireNonNull(cacheRange, "cacheRange 不能为 null");

        if (currentFrameIndex < 0) {
            throw new IllegalArgumentException("currentFrameIndex 不能为负数: " + currentFrameIndex);
        }
        if (requestStartIndex < 0) {
            throw new IllegalArgumentException("requestStartIndex 不能为负数: " + requestStartIndex);
        }
        if (forwardWindowSize < 0) {
            throw new IllegalArgumentException("forwardWindowSize 不能为负数: " + forwardWindowSize);
        }
        if (backwardWindowSize < 0) {
            throw new IllegalArgumentException("backwardWindowSize 不能为负数: " + backwardWindowSize);
        }
        if (totalFrames <= 0) {
            throw new IllegalArgumentException("totalFrames 必须大于 0: " + totalFrames);
        }

        this.currentFrameIndex = currentFrameIndex;
        this.isPrimaryPath = isPrimaryPath;
        this.cacheRange = cacheRange;
        this.requestStartIndex = requestStartIndex;
        this.forwardWindowSize = forwardWindowSize;
        this.backwardWindowSize = backwardWindowSize;
        this.totalFrames = totalFrames;
        this.needPreviousFrame = needPreviousFrame;
    }

    /**
     * 便捷方法：判断当前播放方向是否为正向。
     *
     * @return {@code true} 如果为正向播放，否则为反向播放
     */
    public boolean isForwardDirection() {
        return cacheRange.isForwardDirection;
    }

    /**
     * 根据当前播放方向获取有效的窗口大小。
     *
     * @return 正向播放时返回 forwardWindowSize，反向播放时返回 backwardWindowSize
     */
    public int getEffectiveWindowSize() {
        return isForwardDirection() ? forwardWindowSize : backwardWindowSize;
    }

    @Override
    public String toString() {
        return "EvictionPolicyContext{" +
                "currentFrameIndex=" + currentFrameIndex +
                ", isPrimaryPath=" + isPrimaryPath +
                ", cacheRange=" + cacheRange +
                ", requestStartIndex=" + requestStartIndex +
                ", forwardWindowSize=" + forwardWindowSize +
                ", backwardWindowSize=" + backwardWindowSize +
                ", totalFrames=" + totalFrames +
                ", needPreviousFrame=" + needPreviousFrame +
                '}';
    }
}