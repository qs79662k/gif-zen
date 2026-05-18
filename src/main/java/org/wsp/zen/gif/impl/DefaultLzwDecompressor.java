package org.wsp.zen.gif.impl;

import org.wsp.zen.gif.core.LzwDecompressor;
import org.wsp.zen.gif.exception.LzwCorruptedDataException;
import org.wsp.zen.gif.exception.LzwDecodingException;
import org.wsp.zen.gif.util.PoolUtils;
import org.wsp.zen.pool.core.PoolManager;

/**
 * 默认的 GIF LZW 解压缩器实现，符合 GIF 89a 规范。
 * <p>
 * 该类将 GIF 帧的 LZW 压缩数据解码为原始的像素索引数组（每个字节表示调色板索引）。
 * 解码过程支持动态码字长度、清除码、结束码、隔行扫描等全部特性。
 * </p>
 *
 * <p><b>性能优化：</b>
 * 字典数据结构已优化为单一交错 {@code int[]} 数组，用以提升缓存局部性：
 * 在访问字典条目时，偏移量（offset）和长度（length）紧邻存储在同一个缓存行内，
 * 一次加载即可同时获得两个值。索引计算使用 {@code code << 1} 替代 {@code code * 2}，
 * 减少指令开销。像素索引缓冲区和字典数组优先通过 {@link PoolUtils} 从对象池获取，
 * 以减少高频分配大数组的开销。解码器是无状态的，完全线程安全。
 * </p>
 *
 * <p><b>资源管理：</b>
 * 像素索引缓冲区（{@code pixelIndices}）在解码成功时由调用方负责释放；
 * 解码失败时会通过 {@link PoolUtils} 立即归还给对象池。
 * 字典数组在每次解码结束后无条件归还（若池可用且数组非空）。
 * 所有数组如果长度不足池中的要求，直接丢弃并新建，不归还不符合要求的数组。
 * </p>
 *
 * <p><b>对象池降级：</b>
 * 若 {@code poolManager} 为 {@code null}，所有数组直接新建，功能不受影响。
 * </p>
 *
 * @author wsp
 * @version 1.2
 * @see LzwDecompressor
 * @see PoolManager
 * @see PoolUtils
 */
public class DefaultLzwDecompressor implements LzwDecompressor {

    // ==================== 常量定义（预计算） ====================

    /** 最大码字位长度（GIF 规范限制为 12 位） */
    private static final int MAX_CODE_BITS = 12;

    /** 字典静态最大容量：2^12 = 4096 个条目 */
    private static final int BASE_DICT_SIZE = 1 << MAX_CODE_BITS;

    /** 交错字典实际需要的数据存储长度：offset + length，每项需 2 个 int */
    private static final int MAX_DICT_DATA_SIZE = BASE_DICT_SIZE << 1; // 8192

    /** 隔行扫描的 4 个扫描通道定义：{起始行, 步进} */
    private static final int[][] INTERLACE_PASSES = {
        {0, 8},
        {4, 8},
        {2, 4},
        {1, 2}
    };

    // ==================== 构造器 ====================

    /** 对象池管理器，可为 {@code null} */
    private final PoolManager poolManager;

    /**
     * 构造一个 LZW 解压缩器实例。
     *
     * @param poolManager 对象池管理器，可为 {@code null} 表示不使用对象池
     */
    public DefaultLzwDecompressor(PoolManager poolManager) {
        this.poolManager = poolManager; // 允许 null
    }

    // ==================== 核心解码 ====================

    @Override
    public byte[] decodeFrame(byte[] compressedBuffer, boolean isInterlaced,
                              int minCodeSize, int width, int height)
            throws LzwCorruptedDataException, LzwDecodingException {

        final int totalPixels = width * height;

        // 1. 借用像素索引缓冲区（存储解码后的像素索引值）
        byte[] pixelIndices = PoolUtils.borrowPixelIndices(poolManager, totalPixels);

        // 2. 借用单一交错字典数组（长度 = BASE_DICT_SIZE * 2）
        // 采用交错存储，相邻两个 int 分别存放该字典条目的 offset 和 length，
        // 使用 code << 1 作为索引，code << 1 得到 offset，code << 1 | 1 得到 length。
        // 这种布局使得一次内存访问即可同时获得 offset 和 length，极高的缓存利用率。
        int[] dictData = PoolUtils.borrowDictIntArray(poolManager, MAX_DICT_DATA_SIZE);

        // ---------- LZW 解码状态初始化 ----------
        // 初始基础字典大小：2^minCodeSize（GIF 规范中 minCodeSize 通常为 2~8）
        final int baseDictSize = 1 << minCodeSize;
        // 清除码：等于基础字典大小
        final int clearCode = baseDictSize;
        // 结束码：等于清除码 + 1
        final int eoiCode = clearCode + 1;
        // 下一个可用的字典编码，从 eoiCode + 1 开始
        int nextDictCode = eoiCode + 1;
        // 当前码字位长度，初始为 minCodeSize + 1
        int codeBitLength = minCodeSize + 1;

        // 位流读取状态：当前位偏移和字节索引
        int bitOffset = 0, byteIndex = 0;
        // 前一个解码的字典条目的偏移和长度（用于构建新条目）
        int prevOffset = -1, prevLen = 0, writtenPixels = 0;

        boolean success = false;
        try {
            while (writtenPixels < totalPixels) {
                // 从压缩数据流中读取一个码字
                int inputCode = readCode(compressedBuffer, bitOffset, byteIndex, codeBitLength);
                // 更新位流读取状态
                int endBitOffset = bitOffset + codeBitLength;
                bitOffset = endBitOffset & 7;                 // 保留低 3 位作为新的位偏移
                byteIndex += endBitOffset >>> 3;              // 字节前进（>>>3 代替 /8）

                // ---------- 清除码处理 ----------
                if (inputCode == clearCode) {
                    // 重置字典状态到初始状态
                    nextDictCode = eoiCode + 1;
                    codeBitLength = minCodeSize + 1;
                    prevOffset = -1;
                    prevLen = 0;
                    continue; // 直接读取下一个码字
                }

                // ---------- 结束码处理 ----------
                if (inputCode == eoiCode) {
                    break; // 解码终止
                }

                // ---------- 解码当前码字 ----------
                int currOffset, currLen;
                if (inputCode < baseDictSize) {
                    // 该码字是基础字典中的单像素值（0~baseDictSize-1）
                    // 直接写入像素索引缓冲区的下一个位置
                    pixelIndices[writtenPixels] = (byte) inputCode;
                    currOffset = writtenPixels; // 该像素在缓冲区中的起始位置
                    currLen = 1;                // 长度恒为 1
                } else {
                    // 该码字是之前构建的字典条目
                    if (inputCode > nextDictCode) {
                        // 如果读到的码字大于当前最大的可用字典编码，说明数据损坏
                        throw new LzwDecodingException("字典条目为空: code=" + inputCode);
                    }

                    // 计算该像素序列在当前缓冲区中的写入起始位置
                    // 根据 GIF LZW 规范，当前写入位置 = prevOffset + prevLen
                    int writePos = prevOffset + prevLen;

                    if (inputCode == nextDictCode) {
                        // 特殊情况：输入码字正好等于下一个将要创建的字典条目编码
                        // 此时实际编码的像素序列等于 prev 序列 + prev 序列的第一个像素
                        // 详细说明：
                        // 在向字典添加新条目时，如果新条目的编码恰好就是下一个码字，
                        // 那么该码字解码出的像素序列 = prev 序列 + prev 序列的第一个像素
                        currOffset = writePos;
                        currLen = prevLen + 1;
                        // 先将 prev 序列复制到当前写入位置
                        System.arraycopy(pixelIndices, prevOffset, pixelIndices, currOffset, prevLen);
                        // 再将 prev 序列的第一个像素追加到末尾
                        pixelIndices[currOffset + prevLen] = pixelIndices[prevOffset];
                    } else {
                        // 普通情况：从字典中直接获取该条目的 offset 和 length
                        // 使用 code << 1 快速计算数组索引
                        int idx = inputCode << 1;             // offset 索引（位移替代 *2）
                        currOffset = writePos;
                        currLen = dictData[idx + 1];          // length 在 offset 后相邻位置
                        // 将此字典条目对应的像素序列复制到当前写入位置
                        System.arraycopy(pixelIndices, dictData[idx], pixelIndices, currOffset, currLen);
                    }
                }

                // 增加已写入像素总数
                writtenPixels += currLen;

                // ---------- 向字典添加新条目 ----------
                // 只有在有前一个条目（prevOffset != -1）且字典未满（nextDictCode < BASE_DICT_SIZE）时才添加
                if (prevOffset != -1 && nextDictCode < BASE_DICT_SIZE) {
                    int idx = nextDictCode << 1;              // 同样位移计算存储位置
                    dictData[idx] = prevOffset;                // 存储 offset
                    dictData[idx + 1] = prevLen + 1;           // 存储 length（紧邻存储）
                    nextDictCode++;                            // 增加下一个可用编码
                }

                // 更新前一个条目信息，为下一次迭代准备
                prevOffset = currOffset;
                prevLen = currLen;

                // ---------- 动态码字位长度扩展 ----------
                // 当下一个字典码恰好等于当前位长度所能表示的最大值 (2^codeBitLength) 时，
                // 需要增加码字位长度，以便支持更多的字典条目。
                // 位长度最大不能超过 MAX_CODE_BITS (12)。
                if (nextDictCode == (1 << codeBitLength) && codeBitLength < MAX_CODE_BITS) {
                    codeBitLength++;
                }
            }

            success = true; // 解码成功，标记成功状态
        } finally {
            // 无论解码成功或失败，字典数组都要无条件归还给对象池
            PoolUtils.returnDictIntArray(poolManager, dictData);

            // 如果解码失败，像素索引缓冲区也必须归还（成功时由调用方负责归还）
            if (!success) {
                PoolUtils.returnPixelIndices(poolManager, pixelIndices);
            }
        }

        // 如果需要隔行扫描处理，进行去交织操作
        if (isInterlaced) {
            return deinterlace(pixelIndices, width, height);
        }
        return pixelIndices;
    }

    // ==================== 位读取 ====================

    /**
     * 从压缩数据中读取一个指定长度的码字。
     * <p>
     * 从给定的字节数组 {@code data} 中，从 {@code byteIndex} 位置开始，
     * 以 {@code bitOffset} 作为位偏移，读取 {@code codeBitLength} 个位组成码字。
     * 该方法处理跨字节边界的情况，支持 1 到 4 字节的读取。
     * </p>
     *
     * @param data          压缩数据数组
     * @param bitOffset     当前位偏移（0~7）
     * @param byteIndex     当前字节索引
     * @param codeBitLength 需要读取的码字位数（1~12）
     * @return 读取到的码字（低位对齐）
     * @throws LzwCorruptedDataException 如果数据不足
     */
    private static int readCode(byte[] data, int bitOffset, int byteIndex, int codeBitLength)
            throws LzwCorruptedDataException {
        // 计算读取完这个码字后的总位偏移
        int endBitOffset = bitOffset + codeBitLength;
        // 计算需要跨越的字节数：向上取整除 8（等价于 (endBitOffset + 7) / 8）
        int bytesNeeded = (endBitOffset + 7) >>> 3;
        // 检查剩余数据是否足够
        if (byteIndex + bytesNeeded > data.length) {
            throw new LzwCorruptedDataException("数据不足，无法读取 " + codeBitLength + " 位码字");
        }

        // 根据需要的字节数从字节数组中提取码字
        int code = 0;
        switch (bytesNeeded) {
            case 1:
                code = data[byteIndex] & 0xFF;
                break;
            case 2:
                code = (data[byteIndex] & 0xFF) |
                       ((data[byteIndex + 1] & 0xFF) << 8);
                break;
            case 3:
                code = (data[byteIndex] & 0xFF) |
                       ((data[byteIndex + 1] & 0xFF) << 8) |
                       ((data[byteIndex + 2] & 0xFF) << 16);
                break;
            case 4:
                code = (data[byteIndex] & 0xFF) |
                       ((data[byteIndex + 1] & 0xFF) << 8) |
                       ((data[byteIndex + 2] & 0xFF) << 16) |
                       ((data[byteIndex + 3] & 0xFF) << 24);
                break;
            default:
                throw new LzwCorruptedDataException("不支持的字节数: " + bytesNeeded);
        }

        // 将提取到的码字右移，去掉高位多余位，保留低 codeBitLength 位
        // 原理：将码字左移 (32 - endBitOffset) 再无符号右移 (32 - codeBitLength)，
        // 相当于将有效位对齐到最低位。
        return (code << (32 - endBitOffset)) >>> (32 - codeBitLength);
    }

    // ==================== 去交织 ====================

    /**
     * 将 GIF 交错存储的像素索引数组转换为正常逐行顺序（去交织）。
     * <p>
     * GIF 隔行扫描将图像分为 4 个扫描通道，每个通道的起始行和步进不同。
     * 该方法按照 GIF 规范对交错数据进行重排，生成正常的连续行顺序数组。
     * </p>
     * <p>
     * 会通过对象池复用临时数组，并自动归还原数组。
     * </p>
     *
     * @param source 交错顺序的像素索引数组
     * @param width  图像宽度
     * @param height 图像高度
     * @return 去交织后的像素索引数组，调用方负责归还该数组
     */
    private byte[] deinterlace(byte[] source, int width, int height) {
        // 借用目标缓冲区（至少需要 source.length 大小）
        byte[] target = PoolUtils.borrowPixelIndices(poolManager, source.length);

        int srcPos = 0; // 源数组读取位置
        // 依次处理 4 个扫描通道
        for (int[] pass : INTERLACE_PASSES) {
            int startRow = pass[0]; // 起始行
            int step = pass[1];     // 步进
            for (int row = startRow; row < height; row += step) {
                int dstPos = row * width; // 目标数组写入位置（行起始）
                System.arraycopy(source, srcPos, target, dstPos, width);
                srcPos += width;         // 源数组前进一行
            }
        }

        // 归还原像素索引数组（来自池）
        PoolUtils.returnPixelIndices(poolManager, source);
        return target;
    }
}