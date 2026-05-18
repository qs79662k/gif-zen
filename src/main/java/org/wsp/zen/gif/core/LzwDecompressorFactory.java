package org.wsp.zen.gif.core;

import org.wsp.zen.pool.core.PoolManager;

/**
 * LZW 解压器工厂接口，用于创建 {@link LzwDecompressor} 实例。
 * <p>
 * 该接口允许上层完全自定义 LZW 解压器的实现。通过接收 {@link PoolManager}，
 * 解压器可以自主按名称获取所需的对象池（如像素索引缓冲区池、字典数组池等），
 * 从而完全解耦与特定池实例的绑定。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 工厂实现类应保证线程安全，因为 {@link #create(PoolManager)} 方法可能被多个线程并发调用。
 * 通常，无状态工厂是天然线程安全的；若有状态依赖，需做好同步控制。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * // 1. 准备对象池管理器（已注册解码所需的各种池）
 * PoolManager poolManager = new DefaultPoolManager();
 * poolManager.register(byte[].class, GifPoolNames.PIXEL_INDICES, ...);
 * poolManager.register(int[].class, GifPoolNames.DICT_INT_ARRAY, ...);
 *
 * // 2. 创建解压器工厂
 * LzwDecompressorFactory factory = new DefaultLzwDecompressorFactory();
 *
 * // 3. 创建解压器（注入池管理器）
 * LzwDecompressor decompressor = factory.create(poolManager);
 *
 * // 4. 执行解压（内部按需从管理器获取缓冲区）
 * byte[] pixelIndices = decompressor.decodeFrame(compressedData, true, 8, 320, 240);
 * // 使用完毕后，解压器会负责归还缓冲区到池
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see LzwDecompressor
 * @see PoolManager
 */
public interface LzwDecompressorFactory {

    /**
     * 创建一个 LZW 解压器实例。
     * <p>
     * 解压器在解码过程中需要的所有对象池（如像素索引缓冲区、字典数组等）将通过
     * 传入的 {@link PoolManager} 按名称动态获取。这种设计使得解压器可以独立于具体的
     * 池实例，完全由管理器统一管理资源生命周期。
     * </p>
     *
     * @param poolManager 对象池管理器，提供解码所需的所有对象池，不能为 {@code null}。
     *                    管理器内应至少注册以下名称的池（具体依赖解压器实现）：
     *                    <ul>
     *                      <li>{@code GifPoolNames.PIXEL_INDICES} - 像素索引缓冲区（byte[]）</li>
     *                      <li>{@code GifPoolNames.DICT_INT_ARRAY} - 字典数组（int[]）</li>
     *                      <li>{@code GifPoolNames.ROW_INT_ARRAY} - 行数组（int[]）</li>
     *                      <li>其他可由解压器实现按需扩展</li>
     *                    </ul>
     * @return 新创建的 {@link LzwDecompressor} 实例，不为 {@code null}
     * @throws NullPointerException 如果 {@code poolManager} 为 {@code null}
     */
    LzwDecompressor create(PoolManager poolManager);
}