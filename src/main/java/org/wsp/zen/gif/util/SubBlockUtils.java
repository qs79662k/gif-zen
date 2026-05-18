package org.wsp.zen.gif.util;

import java.util.Objects;
import java.util.zip.DataFormatException;

/**
 * GIF 子块解包工具类，提供去除子块长度标记的功能。
 * <p>
 * 该方法对传入的字节数组进行<b>就地操作</b>，将解包后的连续压缩数据直接覆盖在
 * 输入数组的开头，不额外分配内存。调用者应确保输入数组有足够的空间容纳解包结果
 * （通常输入数组的长度就是原始子块数据的总长度，解包后不会超过它）。
 * </p>
 *
 * <p><b>线程安全性：</b> 纯静态方法，无状态，线程安全。</p>
 *
 * @author wsp
 * @version 1.1
 */
public final class SubBlockUtils {

    // 工具类，禁止实例化
    private SubBlockUtils() {}

    /**
     * 从 GIF 子块结构中提取实际的连续压缩数据流，并<b>就地写入</b>输入数组的开头。
     * <p>
     * 该方法会直接修改 {@code buffer} 的内容，将去除每个子块前的长度字节后的纯数据
     * 从偏移 0 开始依次写入。返回的数组就是传入的 {@code buffer}，但其前
     * {@code writePos} 个字节才是有效的解包数据（有效长度由调用方根据上下文自行确定）。
     * </p>
     *
     * @param buffer 带有子块长度标记的原始数据，解包结果将覆盖其头部
     * @param offset 子块数据的起始偏移
     * @param length 子块数据的总长度
     * @return 传入的同一个 {@code buffer}，其前部包含解包后的连续数据
     * @throws DataFormatException  子块格式错误（如剩余数据不足）
     * @throws NullPointerException 如果 {@code buffer} 为 {@code null}
     */
    public static byte[] unpackSubBlocks(byte[] buffer, int offset, int length)
            throws DataFormatException {
        Objects.requireNonNull(buffer, "缓冲区不能为 null");

        int end = offset + length;
        int writePos = 0;   // 写指针从 0 开始，复用 buffer 自身空间

        while (offset < end) {
            int subBlockSize = buffer[offset++] & 0xff;
            if (subBlockSize == 0) {
                break;
            }
            if (offset + subBlockSize > end) {
                throw new DataFormatException(
                    "子块数据不完整：需要 " + subBlockSize + " 字节，剩余 " + (end - offset));
            }
            // 将子块数据向前移动到 writePos 位置
            System.arraycopy(buffer, offset, buffer, writePos, subBlockSize);
            writePos += subBlockSize;
            offset += subBlockSize;
        }
        // 直接返回原缓冲区，其前 writePos 字节为有效数据
        return buffer;
    }
}