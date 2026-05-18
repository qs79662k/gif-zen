package org.wsp.zen.gif.util;

/**
 * 播放方向智能判断辅助工具类。
 * <p>
 * 该类提供静态方法用于根据连续两次的帧索引请求推断当前的播放方向（正向或反向）。
 * 方向判断考虑了循环边界、跳跃距离和用户回退预期，适用于 GIF 动画播放时的
 * 缓存策略优化与预取逻辑。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该类所有方法均为静态且无状态，因此是线程安全的，可在多线程环境中随意调用。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * int lastRequested = 5;
 * int currentRequested = 6;
 * int totalFrames = 10;
 * boolean lastDirection = true;
 *
 * boolean direction = DirectionHelper.isForwardDirection(
 *         lastRequested, currentRequested, totalFrames, lastDirection);
 * // direction == true（正向）
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.gif.core.Decoder
 */
public final class DirectionHelper {

    private DirectionHelper() {}

    /**
     * 智能判断当前帧请求相对于上一次请求的播放方向。
     * <p>
     * 判断规则：
     * <ul>
     *   <li>初始请求或单帧：默认正向</li>
     *   <li>请求同一帧：沿用上次方向，避免抖动</li>
     *   <li>相邻移动（索引差为 1，含循环边界）：索引增加为正向，减少为反向</li>
     *   <li>跳跃超过 2 帧：一律视为反向（符合用户回退预期）</li>
     *   <li>其他情况：使用环形最短距离，正向步数 ≤ 反向步数则为正向</li>
     * </ul>
     *
     * @param lastRequested 上一次请求的帧索引，{@code -1} 表示初始状态
     * @param currentIndex  当前请求的帧索引，必须在 {@code 0} 到 {@code totalFrames - 1} 之间
     * @param totalFrames   总帧数
     * @param lastDirection 上一次的播放方向，当请求同一帧时沿用此值
     * @return {@code true} 表示正向播放，{@code false} 表示反向播放
     */
    public static boolean isForwardDirection(int lastRequested, int currentIndex, int totalFrames, boolean lastDirection) {
        // 初始或单帧，无方向可判，默认正向
        if (lastRequested == -1 || totalFrames <= 1) {
            return true;
        }
        // 请求同一帧，保持上次方向，避免频繁抖动
        if (lastRequested == currentIndex) {
            return lastDirection;
        }

        // 计算环形相邻判断
        int diff = currentIndex - lastRequested;
        int absDiff = Math.abs(diff);
        boolean isCyclicNeighbor = (lastRequested == totalFrames - 1 && currentIndex == 0)   // 正向循环：末 → 首
                                || (lastRequested == 0 && currentIndex == totalFrames - 1); // 反向循环：首 → 末

        // 规则1：相邻一步（直接相邻或循环边界相邻）
        if (absDiff == 1 || isCyclicNeighbor) {
            // 正向：普通+1，或循环正向（末→首）
            return (diff == 1) || (lastRequested == totalFrames - 1 && currentIndex == 0);
        }

        // 规则2：跳跃超过2帧（环形距离 > 2），直接视为反向
        int forwardDist = (currentIndex - lastRequested + totalFrames) % totalFrames;
        int backwardDist = (lastRequested - currentIndex + totalFrames) % totalFrames;
        int minDist = Math.min(forwardDist, backwardDist);
        if (minDist > 2) {
            return false;
        }

        // 规则3：其余情况（跳跃距离为 2 帧），按最短环形距离判断，相等时优先正向
        return forwardDist <= backwardDist;
    }
}