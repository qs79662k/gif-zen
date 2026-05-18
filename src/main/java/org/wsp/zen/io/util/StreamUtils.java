package org.wsp.zen.io.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.wsp.zen.gif.exception.ParseException;
import org.wsp.zen.gif.model.ChunkedData;
import org.wsp.zen.gif.util.ColorUtils;
import org.wsp.zen.gif.util.PoolUtils;
import org.wsp.zen.pool.core.PoolManager;

/**
 * GIF 流式 I/O 工具类，封装了读取 GIF 二进制格式所需的基本操作。
 * <p>
 * 提供底层读取方法：单个字节、双字节（小端序）、固定长度字符串、
 * GIF 子块结构、颜色表、跳过未知扩展块等。为减少 GC 压力，
 * 子块读取和颜色表读取方法通过 {@link PoolUtils} 复用临时缓冲区。
 * </p>
 *
 * <p><b>降级策略：</b>
 * 使用对象池的方法均接受可为 {@code null} 的 {@link PoolManager}：
 * <ul>
 *   <li>若管理器非空且所需池已注册，则通过 {@link PoolUtils} 从池中获取临时缓冲区。</li>
 *   <li>若管理器为 {@code null} 或池不可用，方法自动新建数组，功能完全正常。</li>
 * </ul>
 * </p>
 *
 * <p><b>资源归属：</b>
 * <ul>
 *   <li>{@link #readChunkedData} 返回的 {@link ChunkedData#compressedBuffer} 数组由调用者负责最终释放
 *        （通常通过缓存监听器归还到对象池）。</li>
 *   <li>{@link #readSubBlocks} 返回的数组为全新创建，由调用者管理，无需归还池。</li>
 *   <li>{@link #readColorTable} 返回的 {@code int[]} 由调用者管理。</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b> 所有方法均为静态且无状态，线程安全。</p>
 *
 * @author wsp
 * @version 1.1
 * @see ChunkedData
 * @see ColorUtils
 * @see PoolManager
 */
public final class StreamUtils {

    // 工具类，禁止实例化
    private StreamUtils() {}

    /** 初始子块缓冲区容量（字节），用于 {@link #readChunkedData} 和 {@link #readSubBlocks} */
    private static final int INITIAL_CHUNK_BUFFER_SIZE = 256;

    // ==================== 基本字节/字符串读取 ====================

    /** 读取一个字节 */
    public static int readByte(InputStream in) throws IOException {
        return in.read();
    }

    /** 读取两个字节，小端序组合为 int */
    public static int readDoubleBytes(InputStream in) throws IOException {
        int b1 = readByte(in);
        int b2 = readByte(in);
        if (b1 == -1 || b2 == -1) {
            throw new EOFException("读取双字节时流意外结束");
        }
        return (b1 & 0xff) | ((b2 & 0xff) << 8);
    }

    /** 读取指定长度的字符串（Latin‑1） */
    public static String readString(InputStream in, int len) throws IOException {
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) {
            int c = readByte(in);
            if (c == -1) throw new EOFException("读取字符串时流意外结束");
            chars[i] = (char) c;
        }
        return new String(chars);
    }

    // ==================== GIF 子块读取 ====================

    /**
     * 读取 GIF 子块结构，返回包含原始块总字节数和数据缓冲区的 {@link ChunkedData}。
     * <p>
     * 该方法从输入流中读取子块链，去除每个子块前的长度字节，将纯数据拼接进一个
     * 连续缓冲区。缓冲区优先通过 {@link PoolUtils} 从压缩数据池借用，
     * 若容量不足则采用倍增扩容策略（新建更大的数组，原数组丢弃且不归还）。最终返回的
     * {@link ChunkedData#compressedBuffer} 即最后一次使用的缓冲区，其长度可能大于有效数据量；
     * 调用者通过 {@link ChunkedData#subBlockTotalBytes} 获取原始块总大小（用于文件偏移），
     * 实际有效数据长度需根据后续解码上下文自行判断。
     * </p>
     * <p>
     * <b>缓冲区的所有权完全转移给调用者，本方法绝不归还或拷贝。</b>
     * 调用者应在使用完毕后（如缓存淘汰时）将 {@code data} 归还给同一对象池。
     * </p>
     * <p><b>降级：</b> 池管理器为 {@code null} 或池未就绪时直接新建初始缓冲区。</p>
     *
     * @param in          输入流
     * @param poolManager 对象池管理器，可为 {@code null}
     * @return 包含原始块大小和数据缓冲区的 ChunkedData
     * @throws IOException 如果读取失败
     */
    public static ChunkedData readChunkedData(InputStream in, PoolManager poolManager) throws IOException {
        // 通过 PoolUtils 借用初始缓冲区（保证长度 ≥ INITIAL_CHUNK_BUFFER_SIZE）
        byte[] buffer = PoolUtils.borrowCompressedDataBuffer(poolManager, INITIAL_CHUNK_BUFFER_SIZE);

        int writePos = 0;          // 已写入纯数据字节数
        int rawTotalSize = 0;      // 原始块总大小（含长度字节）
        int subBlockSize;

        while ((subBlockSize = readByte(in)) > 0) {
            rawTotalSize += subBlockSize + 1;   // 数据长度 + 长度字节

            // 若剩余空间不足，通过倍增方式扩容（减少扩容次数）
            if (buffer.length - writePos < subBlockSize) {
                buffer = growBuffer(buffer, writePos, subBlockSize);
            }
            readFully(in, buffer, writePos, subBlockSize);
            writePos += subBlockSize;
        }
        if (subBlockSize < 0) {
            throw new EOFException("读取子块时流意外结束");
        }
        rawTotalSize++;   // 终止 0 字节

        // 直接返回缓冲区，不截断，不归还
        return new ChunkedData(rawTotalSize, buffer);
    }

    /**
     * 读取 GIF 子块结构，直接返回精确长度的纯数据字节数组。
     * <p>
     * 此方法不依赖对象池，直接通过动态数组扩容实现。初始容量 {@value #INITIAL_CHUNK_BUFFER_SIZE} 字节，
     * 在需要时自动扩容（倍增或精准适应），最后返回长度恰好等于有效数据量的新数组。
     * 适用于不需要原始块大小且数据将被长期持有的场景（如扩展块）。
     * </p>
     *
     * @param in 输入流
     * @return 解包后的纯数据，长度精确
     * @throws IOException 如果读取失败
     */
    public static byte[] readSubBlocks(InputStream in) throws IOException {
        byte[] buffer = new byte[INITIAL_CHUNK_BUFFER_SIZE];
        int writePos = 0;
        int subBlockSize;

        while ((subBlockSize = readByte(in)) > 0) {
            if (buffer.length - writePos < subBlockSize) {
                buffer = growBuffer(buffer, writePos, subBlockSize);
            }
            readFully(in, buffer, writePos, subBlockSize);
            writePos += subBlockSize;
        }
        if (subBlockSize < 0) {
            throw new EOFException("读取子块时流意外结束");
        }
        return Arrays.copyOf(buffer, writePos);
    }

    /**
     * 跳过未知扩展块。
     */
    public static void skipUnknownExtension(InputStream in) throws IOException {
        int subBlockSize;
        while ((subBlockSize = readByte(in)) > 0) {
            readExactBytes(in, subBlockSize);
        }
        if (subBlockSize < 0) {
            throw new EOFException("跳过扩展块时流意外结束");
        }
    }

    // ==================== 颜色表读取 ====================

    /**
     * 读取 GIF 颜色表，返回 ARGB 整型数组。
     * <p>
     * 使用 {@link PoolUtils} 借用固定 768 字节的临时缓冲区，读取后调用 {@link ColorUtils#parseColorTable}
     * 转换为颜色表数组，临时缓冲区在使用完毕后通过 {@link PoolUtils} 立即归还。
     * </p>
     *
     * @param in             输入流
     * @param colorTableSize 颜色数目（2 的幂，2~256）
     * @param poolManager    对象池管理器，可为 {@code null}
     * @return 颜色表 int 数组（长度为 256，前 colorTableSize 个有效）
     * @throws IOException 如果读取失败
     */
    public static int[] readColorTable(InputStream in, int colorTableSize, PoolManager poolManager) throws IOException {
        if (colorTableSize < 2 || colorTableSize > 256 || (colorTableSize & (colorTableSize - 1)) != 0) {
            throw new ParseException("无效的颜色表大小：" + colorTableSize);
        }

        final int fixedByteLen = 256 * 3;
        byte[] chunk = PoolUtils.borrowColorTableBytes(poolManager, fixedByteLen);

        int byteCount = colorTableSize * 3;
        try {
            readFully(in, chunk, 0, byteCount);
            return ColorUtils.parseColorTable(chunk, 0, colorTableSize, poolManager);
        } finally {
            PoolUtils.returnColorTableBytes(poolManager, chunk);
        }
    }

    // ==================== 通用读取 ====================

    /**
     * 精确读取指定长度的字节数组（不使用池）。
     */
    public static byte[] readExactBytes(InputStream in, int len) throws IOException {
        byte[] buffer = new byte[len];
        readFully(in, buffer, 0, len);
        return buffer;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 将缓冲区扩容到至少能容纳 {@code writePos + requiredLength} 个字节，
     * 采用倍增策略（新容量 = max(原长度×2, writePos+requiredLength)）。
     * 原有数据会被复制到新数组，旧数组直接丢弃。
     *
     * @param buffer         当前缓冲区
     * @param writePos       已写入字节数
     * @param requiredLength 需要新增的字节数
     * @return 扩容后的新缓冲区（包含原有数据）
     */
    private static byte[] growBuffer(byte[] buffer, int writePos, int requiredLength) {
        int newLen = Math.max(buffer.length * 2, writePos + requiredLength);
        byte[] newBuffer = new byte[newLen];
        System.arraycopy(buffer, 0, newBuffer, 0, writePos);
        return newBuffer;
    }

    /** 强制读取指定数量的字节到数组指定偏移，若提前结束则抛出 EOFException */
    private static void readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = in.read(buf, off + total, len - total);
            if (r == -1) {
                throw new EOFException("预期读取 " + len + " 字节，实际读取 " + total + " 字节后流结束");
            }
            total += r;
        }
    }
}