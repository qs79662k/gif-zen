package org.wsp.zen.gif.model;

/**
 * GIF 解码器全局对象池名称常量。
 * <p>
 * 所有常量均遵循 {@code "gif-<用途>"} 格式，用于通过 {@link org.wsp.zen.pool.core.PoolManager}
 * 进行注册与检索。调用者应使用这些常量代替硬编码字符串，以确保名称一致性和可维护性。
 * </p>
 *
 * @author wsp
 * @version 1.0
 */
public final class PoolNames {

    private PoolNames() {}

    // ==================== 压缩数据 & 像素索引 (byte[]) ====================

    /** 压缩帧数据读取缓冲区（复用读取的原始压缩字节） */
    public static final String COMPRESSED_DATA = "gif-compressed-data";

    /** 像素索引缓冲区（解码后的颜色索引，对应 pixelIndices） */
    public static final String PIXEL_INDICES = "gif-pixel-indices";

    // ==================== 字典数组 & 行数组 (int[]) ====================

    /** LZW 解码字典数组（固定长度 4096，用于 dictOffsets / dictLengths） */
    public static final String DICT_INT_ARRAY = "gif-dict-int-array";

    /** 渲染时的背景行数组（长度等于图像宽度） */
    public static final String ROW_INT_ARRAY = "gif-row-int-array";

    // ==================== 颜色表相关 ====================

    /** 颜色表原始字节数据（byte[768]，每颜色 3 字节） */
    public static final String COLOR_TABLE_BYTES = "gif-color-table-bytes";

    /** 颜色表整型数组（int[256]，存储解析后的 RGB 颜色值） */
    public static final String COLOR_TABLE_INT = "gif-color-table-int";

    // ==================== 渲染结果 ====================

    /** 渲染结果图像池（BufferedImage 或其他渲染结果类型） */
    public static final String RENDER_IMAGE = "gif-render-image";

}