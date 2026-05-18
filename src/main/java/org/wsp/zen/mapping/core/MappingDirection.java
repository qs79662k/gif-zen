package org.wsp.zen.mapping.core;

/**
 * 窗口映射方向枚举，定义了窗口滑动的方向策略。
 * <p>
 * 用于确定在文件内存映射时，如何根据窗口基准偏移量（{@code windowBaseOffset}）和调整后的窗口大小
 * 计算窗口的起始偏移量和结束偏移量。
 * </p>
 *
 * <ul>
 *   <li>{@link #FORWARD}：以基准偏移量为起点，向右（向文件末尾方向）扩展窗口。</li>
 *   <li>{@link #BACKWARD}：以基准偏移量为终点，向左（向文件开头方向）扩展窗口。</li>
 * </ul>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * MappingDirection direction = MappingDirection.FORWARD;
 * long start = direction.calculateStartOffset(1000, 500);  // 1000
 * long end = direction.calculateEndOffset(1000, 500);      // 1499
 *
 * direction = MappingDirection.BACKWARD;
 * start = direction.calculateStartOffset(1000, 500);       // 501
 * end = direction.calculateEndOffset(1000, 500);           // 1000
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.mapping.model.MappingContext
 */
public enum MappingDirection {

    /**
     * 正向映射方向：以 {@code windowBaseOffset} 作为窗口的起始点，向右扩展。
     * <p>
     * 起始偏移量 = {@code windowBaseOffset}<br>
     * 结束偏移量 = {@code windowBaseOffset + adjustedWindowSize - 1}
     * </p>
     */
    FORWARD {
        @Override
        public long calculateStartOffset(long windowBaseOffset, long adjustedWindowSize) {
            return windowBaseOffset;
        }

        @Override
        public long calculateEndOffset(long windowBaseOffset, long adjustedWindowSize) {
            return windowBaseOffset + adjustedWindowSize - 1;
        }
    },

    /**
     * 反向映射方向：以 {@code windowBaseOffset} 作为窗口的结束点，向左扩展。
     * <p>
     * 起始偏移量 = {@code windowBaseOffset - adjustedWindowSize + 1}<br>
     * 结束偏移量 = {@code windowBaseOffset}
     * </p>
     */
    BACKWARD {
        @Override
        public long calculateStartOffset(long windowBaseOffset, long adjustedWindowSize) {
            return windowBaseOffset - adjustedWindowSize + 1;
        }

        @Override
        public long calculateEndOffset(long windowBaseOffset, long adjustedWindowSize) {
            return windowBaseOffset;
        }
    };

    /**
     * 根据窗口基准偏移量和调整后的窗口大小，计算窗口的起始偏移量。
     *
     * @param windowBaseOffset   窗口基准偏移量（单位：字节），必须 ≥ 0
     * @param adjustedWindowSize 调整后的窗口大小（单位：字节），必须 ≥ 1
     * @return 窗口的起始偏移量（字节）
     */
    public abstract long calculateStartOffset(long windowBaseOffset, long adjustedWindowSize);

    /**
     * 根据窗口基准偏移量和调整后的窗口大小，计算窗口的结束偏移量。
     *
     * @param windowBaseOffset   窗口基准偏移量（单位：字节），必须 ≥ 0
     * @param adjustedWindowSize 调整后的窗口大小（单位：字节），必须 ≥ 1
     * @return 窗口的结束偏移量（字节）
     */
    public abstract long calculateEndOffset(long windowBaseOffset, long adjustedWindowSize);
}