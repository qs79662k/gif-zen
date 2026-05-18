package org.wsp.zen.gif.util;

import java.util.Objects;

import org.wsp.zen.gif.model.PoolNames;
import org.wsp.zen.pool.core.ObjectPool;
import org.wsp.zen.pool.core.PoolManager;

/**
 * 对象池工具类，为 {@link PoolNames} 中的每种对象池提供专用的借用与归还方法。
 * <p>
 * 所有借用方法在池不可用或借出对象长度/尺寸不足时，会新建符合要求的对象并返回，
 * 不会归还不符合要求的对象；归还方法在池不可用或对象为 {@code null} 时直接返回。
 * 对渲染图像池，额外的宽高验证确保借出的图像与画布尺寸匹配，不匹配时将通过工厂创建新图。
 * </p>
 *
 * <p><b>命名约定：</b> 方法名以 {@code borrow} / {@code return} 成对出现，并通过名称指明对应的池，
 * 调用者无需记忆池的字符串键名。</p>
 *
 * @author wsp
 * @version 1.2
 * @see PoolNames
 * @see PoolManager
 */
public final class PoolUtils {

    // 工具类，禁止实例化
    private PoolUtils() {}

    // ==================== 渲染图像工厂接口 ====================

    /**
     * 渲染图像工厂接口，用于创建指定尺寸的渲染结果对象。
     *
     * @param <T> 渲染结果类型（如 BufferedImage）
     */
    @FunctionalInterface
    public interface RenderImageFactory<T> {
        T create(int width, int height);
    }

    // ==================== 渲染图像池 ====================

    /**
     * 从渲染图像池中借出一个与画布尺寸匹配的渲染对象。
     * 需要提供渲染对象的 {@link Class} 以正确匹配池中的类型键。
     * 若池不可用、借出为 null 或尺寸不匹配，则使用工厂创建新对象并归还旧对象（如果有）。
     *
     * @param poolManager 对象池管理器，可为 {@code null}
     * @param type        渲染对象的具体类型（如 {@code BufferedImage.class}），不能为 {@code null}
     * @param width       画布宽度（像素）
     * @param height      画布高度（像素）
     * @param factory     用于创建新渲染对象的工厂
     * @param <T>         渲染结果类型
     * @return 尺寸匹配的渲染对象
     */
    public static <T> T borrowRenderImage(PoolManager poolManager, Class<T> type,
                                          int width, int height, RenderImageFactory<T> factory) {
        Objects.requireNonNull(factory, "渲染对象工厂不能为 null");
        Objects.requireNonNull(type, "渲染对象类型不能为 null");
        if (poolManager == null) return factory.create(width, height);

        ObjectPool<T> pool = poolManager.getPool(type, PoolNames.RENDER_IMAGE);
        if (pool == null) return factory.create(width, height);

        T image = pool.obtain();
        if (image == null) return factory.create(width, height);

        // 检查尺寸是否匹配
        int imgWidth = getImageWidth(image);
        int imgHeight = getImageHeight(image);
        if (imgWidth != width || imgHeight != height) {
            // 尺寸不匹配，归还并从工厂创建新对象
            pool.release(image);
            return factory.create(width, height);
        }
        return image;
    }

    /**
     * 将渲染对象归还到渲染图像池。需要提供渲染对象的 {@link Class} 以正确匹配池中的类型键。
     *
     * @param poolManager 对象池管理器，可为 {@code null}
     * @param type        渲染对象的具体类型，不能为 {@code null}
     * @param image       要归还的对象，可为 {@code null}
     * @param <T>         渲染结果类型
     */
    public static <T> void returnRenderImage(PoolManager poolManager, Class<T> type, T image) {
        Objects.requireNonNull(type, "渲染对象类型不能为 null");
        if (image == null || poolManager == null) return;
        ObjectPool<T> pool = poolManager.getPool(type, PoolNames.RENDER_IMAGE);
        if (pool != null) pool.release(image);
    }

    // ==================== 像素索引缓冲区 (byte[]) ====================

    public static byte[] borrowPixelIndices(PoolManager poolManager, int minLength) {
        return borrowByteArray(poolManager, PoolNames.PIXEL_INDICES, minLength);
    }

    public static void returnPixelIndices(PoolManager poolManager, byte[] array) {
        returnByteArray(poolManager, PoolNames.PIXEL_INDICES, array);
    }

    // ==================== 压缩数据缓冲区 (byte[]) ====================

    public static byte[] borrowCompressedDataBuffer(PoolManager poolManager, int minLength) {
        return borrowByteArray(poolManager, PoolNames.COMPRESSED_DATA, minLength);
    }

    public static void returnCompressedDataBuffer(PoolManager poolManager, byte[] array) {
        returnByteArray(poolManager, PoolNames.COMPRESSED_DATA, array);
    }

    // ==================== 颜色表字节数组 (byte[]) ====================

    public static byte[] borrowColorTableBytes(PoolManager poolManager, int minLength) {
        return borrowByteArray(poolManager, PoolNames.COLOR_TABLE_BYTES, minLength);
    }

    public static void returnColorTableBytes(PoolManager poolManager, byte[] array) {
        returnByteArray(poolManager, PoolNames.COLOR_TABLE_BYTES, array);
    }

    // ==================== 字典整型数组 (int[]) ====================

    public static int[] borrowDictIntArray(PoolManager poolManager, int minLength) {
        return borrowIntArray(poolManager, PoolNames.DICT_INT_ARRAY, minLength);
    }

    public static void returnDictIntArray(PoolManager poolManager, int[] array) {
        returnIntArray(poolManager, PoolNames.DICT_INT_ARRAY, array);
    }

    // ==================== 行数组 (int[]) ====================

    public static int[] borrowRowIntArray(PoolManager poolManager, int minLength) {
        return borrowIntArray(poolManager, PoolNames.ROW_INT_ARRAY, minLength);
    }

    public static void returnRowIntArray(PoolManager poolManager, int[] array) {
        returnIntArray(poolManager, PoolNames.ROW_INT_ARRAY, array);
    }

    // ==================== 颜色表整型数组 (int[]) ====================

    public static int[] borrowColorTableInt(PoolManager poolManager, int minLength) {
        return borrowIntArray(poolManager, PoolNames.COLOR_TABLE_INT, minLength);
    }

    public static void returnColorTableInt(PoolManager poolManager, int[] array) {
        returnIntArray(poolManager, PoolNames.COLOR_TABLE_INT, array);
    }

    // ==================== 内部通用方法 ====================

    private static byte[] borrowByteArray(PoolManager poolManager, String poolName, int requiredLength) {
        if (poolManager == null) return new byte[requiredLength];
        ObjectPool<byte[]> pool = poolManager.getPool(byte[].class, poolName);
        if (pool == null) return new byte[requiredLength];
        byte[] buf = pool.obtain();
        if (buf == null || buf.length < requiredLength) return new byte[requiredLength];
        return buf;
    }

    private static void returnByteArray(PoolManager poolManager, String poolName, byte[] array) {
        if (array == null || poolManager == null) return;
        ObjectPool<byte[]> pool = poolManager.getPool(byte[].class, poolName);
        if (pool != null) pool.release(array);
    }

    private static int[] borrowIntArray(PoolManager poolManager, String poolName, int requiredLength) {
        if (poolManager == null) return new int[requiredLength];
        ObjectPool<int[]> pool = poolManager.getPool(int[].class, poolName);
        if (pool == null) return new int[requiredLength];
        int[] arr = pool.obtain();
        if (arr == null || arr.length < requiredLength) return new int[requiredLength];
        return arr;
    }

    private static void returnIntArray(PoolManager poolManager, String poolName, int[] array) {
        if (array == null || poolManager == null) return;
        ObjectPool<int[]> pool = poolManager.getPool(int[].class, poolName);
        if (pool != null) pool.release(array);
    }

    // ==================== 渲染图像尺寸提取（内省） ====================

    private static int getImageWidth(Object image) {
        if (image instanceof java.awt.Image) {
            return ((java.awt.Image) image).getWidth(null);
        }
        try {
            return (int) image.getClass().getMethod("getWidth").invoke(image);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法获取渲染对象的宽度", e);
        }
    }

    private static int getImageHeight(Object image) {
        if (image instanceof java.awt.Image) {
            return ((java.awt.Image) image).getHeight(null);
        }
        try {
            return (int) image.getClass().getMethod("getHeight").invoke(image);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法获取渲染对象的高度", e);
        }
    }
}