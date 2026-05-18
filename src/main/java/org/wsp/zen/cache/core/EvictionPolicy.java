package org.wsp.zen.cache.core;

/**
 * 缓存驱逐策略接口，用于判断给定键是否可以被从缓存中移除。
 * <p>
 * 实现类可根据具体算法（如 LRU、LFU、FIFO 或基于时间戳的过期策略）来决定某个缓存项是否满足驱逐条件。
 * 此接口通常与 {@link Cache} 或帧缓存管理器配合使用，以控制缓存的容量和生命周期。
 * </p>
 *
 * @param <K> 缓存键的类型，例如 {@link Integer}（帧索引）、{@link String}（资源标识符）等
 * @author wsp
 * @version 1.0
 */
public interface EvictionPolicy<K> {

    /**
     * 判断指定的缓存键是否应被驱逐。
     * <p>
     * 当缓存需要释放空间时（例如达到容量上限或执行周期性清理），会调用此方法逐个检查缓存项。
     * 实现类应根据内部状态（如最近访问时间、访问频率、插入时间等）返回 {@code true} 表示该键对应的
     * 条目应当被移除，返回 {@code false} 表示保留。
     * </p>
     *
     * @param key 待检查的缓存键，不能为 {@code null}
     * @return {@code true} 如果该键对应的缓存项应该被驱逐；{@code false} 否则
     * @throws NullPointerException 如果 {@code key} 为 {@code null}（可选，但建议显式检查）
     */
    boolean isEvictable(K key);

    /**
     * 根据总帧数和原始窗口大小，计算调整后的有效窗口大小。
     * <p>
     * 此静态工具方法用于帧缓存或窗口式缓存场景，确保窗口大小合法且不超出总帧数范围。
     * 调整规则如下：
     * <ul>
     *   <li>如果 {@code totalFrameCount <= 0}，返回 {@code 0}（表示没有有效帧）</li>
     *   <li>如果 {@code windowSize == -1}（特殊值，表示“不驱逐任何帧”），返回 {@code totalFrameCount}
     *       （覆盖所有帧，等效于保留全部帧，不触发驱逐）</li>
     *   <li>如果 {@code windowSize > totalFrameCount}，返回 {@code totalFrameCount}
     *       （窗口大小自动裁剪到最大帧数）</li>
     *   <li>其他情况返回原始的 {@code windowSize}</li>
     * </ul>
     * </p>
     *
     * @param totalFrameCount GIF 或图像序列的总帧数，必须 ≥ 0
     * @param windowSize      期望的窗口大小，支持值：
     *                        <ul>
     *                          <li>-1：特殊值，表示不驱逐任何帧（返回 totalFrameCount）</li>
     *                          <li>正数：期望保留的帧数量，如果大于总帧数则自动调整为总帧数</li>
     *                          <li>0 或负数（除 -1 外）：行为未定义，建议调用前保证合法</li>
     *                        </ul>
     * @return 调整后的有效窗口大小，范围为 {@code [0, totalFrameCount]}
     */
    static int computeAdjustedWindowSize(int totalFrameCount, int windowSize) {
        if (totalFrameCount <= 0) {
            return 0;
        }

        // 窗口大小合法性调整：
        // 特殊值 -1（不驱逐任何帧）-> 调整为总帧数（覆盖所有帧，等价于不驱逐）
        // 原始窗口大小超过总帧数 -> 调整为总帧数（避免窗口超出有效帧范围）
        return ((windowSize == -1 || windowSize > totalFrameCount) ? totalFrameCount : windowSize);
    }
}