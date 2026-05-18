package org.wsp.zen.gif.model;

/**
 * GIF 解码器全局缓存名称常量。
 * <p>
 * 所有常量均遵循 {@code "gif-<用途>"} 格式，用于通过 {@link org.wsp.zen.cache.core.CacheManager}
 * 进行注册与检索。调用者应使用这些常量代替硬编码字符串，以确保名称一致性和可维护性。
 * 与 {@link PoolNames} 对应，但不包含对象池名称。
 * </p>
 *
 * @author wsp
 * @version 1.0
 */
public final class CacheNames {

    private CacheNames() {}

    // ==================== 三级缓存 ====================

    /** 一级缓存：原始帧数据（压缩的像素索引数据 + 帧元数据） */
    public static final String FRAME_DATA_CACHE = "gif-frame-data-cache";

    /** 二级缓存：解码后的帧数据（包含颜色索引数组和透明色等信息） */
    public static final String DECODE_FRAME_CACHE = "gif-decode-frame-cache";

    /** 三级缓存：渲染后的最终结果（例如 BufferedImage 对象） */
    public static final String RENDER_FRAME_CACHE = "gif-render-frame-cache";

}