package org.wsp.zen.gif.core;

import org.wsp.zen.gif.exception.LzwCorruptedDataException;

/**
 * GIF LZW 解压缩器接口，用于解码经过 LZW 压缩的图像数据。
 * <p>
 * GIF 规范中，图像数据（包括索引流）采用 LZW 算法压缩。此接口定义了解码方法，
 * 实现类应遵循 GIF 标准中的 LZW 解压逻辑，处理交错的像素排列，并支持变长码字。
 * </p>
 *
 * <p><b>资源管理：</b>
 * 解码得到的像素数组可能由对象池管理，但资源释放已统一由解码流程中的缓存监听器
 * 或调用方通过对象池工具类完成，因此本接口不再提供显式的 {@code release} 方法。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * LzwDecompressor decompressor = new MyLzwDecompressor();
 * byte[] compressedData = ...; // 获取压缩数据
 * byte[] decoded = decompressor.decodeFrame(compressedData, true, 8, 320, 240);
 * // 使用 decoded 数组构建图像
 * // 最终的数组释放由缓存淘汰监听器或对象池工具类负责
 * }</pre>
 *
 * <p><b>关于交错（Interlaced）：</b>
 * 如果 GIF 图像数据标记为交错模式，解压缩后应按照 GIF 规范的顺序重新排列像素：
 * <ol>
 *   <li>第 0 行，第 8 行，第 16 行……（步长 8）</li>
 *   <li>第 4 行，第 12 行，第 20 行……（步长 8）</li>
 *   <li>第 2 行，第 10 行，第 18 行……（步长 4）</li>
 *   <li>第 1 行，第 9 行，第 17 行……（步长 2）</li>
 * </ol>
 * 非交错模式按从上到下的自然顺序输出。
 * </p>
 *
 * <p><b>异常处理：</b>
 * 当压缩数据不符合 LZW 格式或解码过程中出现码字溢出、码表错误等情况时，
 * 应抛出 {@link LzwCorruptedDataException}，避免解码器继续处理无效数据。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 实现类通常设计为无状态或线程封闭的，因此不要求线程安全。多个线程不应同时使用同一实例解码不同帧。
 * 若需并发解码多帧，应创建独立的解压缩器实例。
 * </p>
 *
 * @author wsp
 * @version 1.1
 * @see org.wsp.zen.gif.exception.LzwCorruptedDataException
 */
public interface LzwDecompressor {

    /**
     * 解码单个 GIF 图像帧的压缩数据。
     *
     * @param compressedBuffer  压缩后的 LZW 数据流（通常已去除块长度标记，为纯码流），
     *                        不能为 {@code null} 或空数组
     * @param isInterlaced    是否采用交错存储模式。{@code true} 表示该帧为交错格式，
     *                        解码后需按交错规则重排像素行；{@code false} 表示逐行存储
     * @param lzwMinCodeSize  LZW 初始码字大小（Code Size），范围通常为 2 到 12（含）。
     *                        实际解压缩过程中码字位数会动态增长，最大不超过 12 位
     * @param width           图像的宽度（像素数），用于确定输出数组的长度和交错行计算
     * @param height          图像的高度（像素数），用于确定输出数组的长度和交错行计算
     * @return 解码后的像素索引数组，长度为 {@code width * height}，每个元素为 0-255 的调色板索引（byte 类型）。
     *         输出顺序为从上到下、从左到右（经过交错重排后）。
     * @throws LzwCorruptedDataException 如果压缩数据损坏、码字超出范围、码表构建错误或遇到其他违反 LZW 规范的情况
     * @throws IllegalArgumentException  如果 {@code compressedData} 为 {@code null} 或空数组，
     *                                   {@code lzwMinCodeSize} 超出有效范围（2-12），
     *                                   或 {@code width <= 0} 或 {@code height <= 0}
     */
    byte[] decodeFrame(byte[] compressedBuffer, boolean isInterlaced, int lzwMinCodeSize, int width, int height)
            throws LzwCorruptedDataException;
}