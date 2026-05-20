package org.wsp.zen.gif.impl;

import org.wsp.zen.gif.core.LzwDecompressor;
import org.wsp.zen.gif.exception.LzwCorruptedDataException;
import org.wsp.zen.gif.exception.LzwDecodingException;
import org.wsp.zen.gif.util.PoolUtils;
import org.wsp.zen.pool.core.PoolManager;

/**
 * 默认 GIF LZW 解压缩器，符合 GIF 89a 规范。
 * <p>
 * 将 LZW 压缩数据解码为像素索引数组（每字节一个调色板索引），
 * 支持动态码长、清除码、结束码和隔行扫描。
 * </p>
 *
 * <h3>数据结构</h3>
 * 字典使用单一 {@code int[]} 交错存储，offset 与 length 紧邻，
 * 一次缓存行加载即可获取两个值，索引通过 {@code code << 1} 计算。
 *
 * <h3>对象池</h3>
 * 像素缓冲区和字典数组优先从对象池借用，降低高频分配开销。
 * 解码成功时像素缓冲区由调用方释放；失败时自动归还。
 * 字典数组每次解码结束后无条件归还。
 * 若对象池不可用（{@code poolManager == null}），直接新建数组。
 *
 * <h3>线程安全</h3>
 * 解码器无状态，完全线程安全。
 *
 * @author wsp
 * @version 1.3
 */
public class DefaultLzwDecompressor implements LzwDecompressor {

    /** GIF 规范最大码长：12 位 */
    private static final int MAX_CODE_BITS = 12;

    /** 字典最大条目数：2^12 = 4096 */
    private static final int BASE_DICT_SIZE = 1 << MAX_CODE_BITS;

    /** 交错字典数据长度：每项两个 int（offset, length） */
    private static final int MAX_DICT_DATA_SIZE = BASE_DICT_SIZE << 1; // 8192

    /** 隔行扫描通道定义：{起始行, 步进} */
    private static final int[][] INTERLACE_PASSES = {
            {0, 8}, {4, 8}, {2, 4}, {1, 2}
    };

    private final PoolManager poolManager;

    /**
     * @param poolManager 对象池管理器，可为 null
     */
    public DefaultLzwDecompressor(PoolManager poolManager) {
        this.poolManager = poolManager;
    }

    // ==================== 核心解码 ====================

    @Override
    public byte[] decodeFrame(byte[] compressedBuffer, boolean isInterlaced,
                              int minCodeSize, int width, int height)
            throws LzwCorruptedDataException, LzwDecodingException {

        // ---------- 参数校验 ----------
        if (compressedBuffer == null) {
            throw new IllegalArgumentException("compressedBuffer 不能为 null");
        }
        if (compressedBuffer.length == 0) {
            throw new LzwCorruptedDataException("压缩数据为空");
        }
        if (minCodeSize < 2 || minCodeSize > 8) {
            throw new IllegalArgumentException(
                    "minCodeSize 必须在 [2, 8] 之间，实际: " + minCodeSize);
        }
        // GIF 中宽高为 16 位无符号整数，逻辑上至少为 1
        if (width < 1 || width > 0xFFFF) {
            throw new IllegalArgumentException(
                    "width 必须在 [1, 65535] 之间，实际: " + width);
        }
        if (height < 1 || height > 0xFFFF) {
            throw new IllegalArgumentException(
                    "height 必须在 [1, 65535] 之间，实际: " + height);
        }
        // 防止乘积溢出 int 范围
        if (width > Integer.MAX_VALUE / height) {
            throw new IllegalArgumentException(
                    "图像尺寸过大，总像素数超出 int 最大值: width=" + width + ", height=" + height);
        }

        final int totalPixels = width * height;

        // 1. 借用缓冲区
        byte[] pixelIndices = PoolUtils.borrowPixelIndices(poolManager, totalPixels);
        int[] dictData = PoolUtils.borrowDictIntArray(poolManager, MAX_DICT_DATA_SIZE);

        // 2. 初始化 LZW 状态
        final int baseDictSize = 1 << minCodeSize;
        final int clearCode = baseDictSize;
        final int eoiCode = clearCode + 1;
        int nextDictCode = eoiCode + 1;
        int codeBitLength = minCodeSize + 1;

        int bitOffset = 0, byteIndex = 0;
        int prevOffset = -1, prevLen = 0;
        int writtenPixels = 0;

        boolean success = false;
        try {
            while (writtenPixels < totalPixels) {
                // 读取一个码字
                int inputCode = readCode(compressedBuffer, bitOffset, byteIndex, codeBitLength);
                int endBitOffset = bitOffset + codeBitLength;
                bitOffset = endBitOffset & 7;
                byteIndex += endBitOffset >>> 3;

                // 清除码：重置字典
                if (inputCode == clearCode) {
                    nextDictCode = eoiCode + 1;
                    codeBitLength = minCodeSize + 1;
                    prevOffset = -1;
                    prevLen = 0;
                    continue;
                }

                // 结束码：正常终止
                if (inputCode == eoiCode) {
                    break;
                }

                // 解码当前码字为 (currOffset, currLen)
                int currOffset, currLen;
                if (inputCode < baseDictSize) {
                    // 根节点：单像素索引
                    if (writtenPixels >= totalPixels) {
                        throw new LzwCorruptedDataException("像素缓冲区溢出（根节点）");
                    }
                    pixelIndices[writtenPixels] = (byte) inputCode;
                    currOffset = writtenPixels;
                    currLen = 1;
                } else {
                    // 字典条目
                    if (inputCode > nextDictCode) {
                        throw new LzwDecodingException("未定义的字典条目: " + inputCode);
                    }
                    int writePos = prevOffset + prevLen;
                    if (inputCode == nextDictCode) {
                        // KwKwK 特殊情况：序列 = prev + prev[0]
                        currOffset = writePos;
                        currLen = prevLen + 1;
                        System.arraycopy(pixelIndices, prevOffset,
                                pixelIndices, currOffset, prevLen);
                        pixelIndices[currOffset + prevLen] = pixelIndices[prevOffset];
                    } else {
                        int idx = inputCode << 1;
                        currOffset = writePos;
                        currLen = dictData[idx + 1];
                        System.arraycopy(pixelIndices, dictData[idx],
                                pixelIndices, currOffset, currLen);
                    }
                }

                // 边界保护：防止写入超出图像尺寸
                if (writtenPixels + currLen > totalPixels) {
                    throw new LzwCorruptedDataException(
                            "解码像素数超出图像尺寸: total=" + totalPixels +
                                    ", written=" + writtenPixels + ", len=" + currLen);
                }
                writtenPixels += currLen;

                // 向字典添加新条目（若字典未满）
                if (prevOffset != -1 && nextDictCode < BASE_DICT_SIZE) {
                    int idx = nextDictCode << 1;
                    dictData[idx] = prevOffset;
                    dictData[idx + 1] = prevLen + 1;
                    nextDictCode++;
                }

                // 更新前一条目信息
                prevOffset = currOffset;
                prevLen = currLen;

                // 码字长度扩展
                if (nextDictCode == (1 << codeBitLength) && codeBitLength < MAX_CODE_BITS) {
                    codeBitLength++;
                }
            }

            // 解码完成后像素数必须与图像尺寸完全一致
            if (writtenPixels != totalPixels) {
                throw new LzwCorruptedDataException(
                        "解码像素数不匹配: expected=" + totalPixels +
                                ", actual=" + writtenPixels);
            }
            success = true;
        } finally {
            PoolUtils.returnDictIntArray(poolManager, dictData);
            if (!success) {
                PoolUtils.returnPixelIndices(poolManager, pixelIndices);
            }
        }

        if (isInterlaced) {
            return deinterlace(pixelIndices, width, height);
        }
        return pixelIndices;
    }

    // ==================== 位读取 ====================

    /**
     * 从字节数组中读取指定长度的码字（低位对齐）。
     *
     * @param data          压缩数据
     * @param bitOffset     当前位偏移 (0~7)
     * @param byteIndex     当前字节索引
     * @param codeBitLength 码字位数 (1~12)
     * @return 读取到的码字
     * @throws LzwCorruptedDataException 数据不足
     */
    private static int readCode(byte[] data, int bitOffset, int byteIndex, int codeBitLength)
            throws LzwCorruptedDataException {
        int endBitOffset = bitOffset + codeBitLength;
        int bytesNeeded = (endBitOffset + 7) >>> 3;
        if (byteIndex + bytesNeeded > data.length) {
            throw new LzwCorruptedDataException(
                    "数据不足，无法读取 " + codeBitLength + " 位码字");
        }

        int code;
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
                throw new LzwCorruptedDataException("非法读取字节数: " + bytesNeeded);
        }

        // 右移保留有效位
        return (code << (32 - endBitOffset)) >>> (32 - codeBitLength);
    }

    // ==================== 去交织 ====================

    /**
     * 将隔行扫描的像素索引数组还原为顺序行。
     * <p>
     * 源数组会被自动归还池，返回的新数组由调用方负责释放。
     * </p>
     *
     * @param source 隔行数据（长度等于 width * height）
     * @param width  图像宽度
     * @param height 图像高度
     * @return 连续行排列的像素索引数组
     */
    private byte[] deinterlace(byte[] source, int width, int height) {
        byte[] target = PoolUtils.borrowPixelIndices(poolManager, width * height);
        int srcPos = 0;
        for (int[] pass : INTERLACE_PASSES) {
            int startRow = pass[0];
            int step = pass[1];
            for (int row = startRow; row < height; row += step) {
                int dstPos = row * width;
                System.arraycopy(source, srcPos, target, dstPos, width);
                srcPos += width;
            }
        }

        PoolUtils.returnPixelIndices(poolManager, source);
        return target;
    }
}