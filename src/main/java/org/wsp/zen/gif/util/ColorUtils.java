package org.wsp.zen.gif.util;

import java.util.Objects;

import org.wsp.zen.pool.core.PoolManager;

/**
 * 颜色转换与解析工具类，提供 GIF 颜色表解析和 ARGB 操作。
 * <p>
 * 解析方法内部会通过 {@link PoolUtils} 从对象池借用固定 256 长度的 {@code int[]} 临时数组，
 * 若池不可用或长度不足，则自动新建（降级）。填充后的数组直接返回，
 * <b>长度固定为 256</b>，调用者仅使用前 {@code entryCount} 个元素。
 * </p>
 *
 * <p><b>资源管理：</b>
 * 返回的数组由上层（如缓存淘汰监听器）负责归还给池。
 * </p>
 *
 * <p><b>线程安全性：</b> 无状态，线程安全。</p>
 *
 * @author wsp
 * @version 1.1
 */
public final class ColorUtils {

    private ColorUtils() {}

    /** GIF 最大颜色数 */
    private static final int MAX_COLORS = 256;

    /**
     * 将原始颜色表字节转换为 ARGB 整型数组。
     *
     * @param colorTableBytes 颜色表原始字节数组（长度 ≥ offset + entryCount * 3）
     * @param offset          字节起始偏移
     * @param entryCount      实际颜色条目数（≤ 256）
     * @param poolManager     对象池管理器，可为 {@code null}
     * @return 长度固定为 256 的 int 数组，前 entryCount 个元素有效
     */
    public static int[] parseColorTable(byte[] colorTableBytes, int offset,
                                        int entryCount, PoolManager poolManager) {
        Objects.requireNonNull(colorTableBytes);

        // 通过 PoolUtils 借用颜色表整型数组（或新建）
        int[] colorTable = PoolUtils.borrowColorTableInt(poolManager, MAX_COLORS);

        for (int i = 0; i < entryCount; i++) {
            int r = colorTableBytes[offset + i * 3] & 0xff;
            int g = colorTableBytes[offset + i * 3 + 1] & 0xff;
            int b = colorTableBytes[offset + i * 3 + 2] & 0xff;
            colorTable[i] = rgbToInt(r, g, b);
        }
        // 直接返回，由上层负责归还
        return colorTable;
    }

    // ======== 颜色分量工具方法 ========
    public static int rgbToInt(int r, int g, int b) { return rgbToInt(0xff, r, g, b); }
    public static int rgbToInt(int alpha, int r, int g, int b) {
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }
    public static int getRed(int color)   { return (color >> 16) & 0xff; }
    public static int getGreen(int color) { return (color >> 8) & 0xff; }
    public static int getBlue(int color)  { return color & 0xff; }
    public static int getAlpha(int color) { return (color >> 24) & 0xff; }
}