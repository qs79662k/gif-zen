package org.wsp.zen.gif.model;

import java.util.Objects;

/**
 * GIF 分块数据容器，封装解包后的纯压缩数据缓冲区及原始子块结构的字节大小。
 * <p>
 * 该类在 GIF 解析过程中传递压缩帧数据。它保留了原始子块结构占用的总字节数
 * （包含每个子块的长度字节和终止的 0 字节），以便在随机文件读取时能正确定位
 * 数据范围；同时提供解包后的连续压缩数据缓冲区，供 LZW 解码等后续处理使用。
 * </p>
 *
 * <p><b>字段说明：</b>
 * <ul>
 *   <li>{@link #subBlockTotalBytes}：原始子块结构的总字节数，用于文件偏移量计算。</li>
 *   <li>{@link #compressedBuffer}：解包后的纯压缩数据缓冲区。<b>注意：该数组可能比实际有效
 *       数据长</b>（例如从对象池借用的数组），有效数据的长度应由调用者根据具体
 *       帧信息（如像素数）或解包时记录的长度确定。缓冲区所有权随对象转移，
 *       调用者可在使用完毕后将其归还给对应的对象池。</li>
 * </ul>
 * </p>
 *
 * <p><b>不可变性：</b> 字段均为 final，构造后不可更改，是线程安全的。</p>
 *
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.io.util.StreamUtils
 */
public class ChunkedData {

    /** 原始子块结构占用的总字节数（包含长度标记字节和终止 0 字节），必须 ≥ 0 */
    public final int subBlockTotalBytes;

    /** 解包后的纯压缩数据缓冲区（可能比实际有效数据长） */
    public final byte[] compressedBuffer;

    /**
     * 构造一个分块数据对象。
     *
     * @param subBlockTotalBytes 原始子块结构总字节数，必须 ≥ 0
     * @param compressedBuffer   解包后的纯压缩数据缓冲区，不能为 {@code null}，
     *                           其长度可能大于有效数据量
     * @throws NullPointerException     如果 {@code compressedBuffer} 为 {@code null}
     * @throws IllegalArgumentException 如果 {@code subBlockTotalBytes < 0}
     */
    public ChunkedData(int subBlockTotalBytes, byte[] compressedBuffer) {
        if (subBlockTotalBytes < 0) {
            throw new IllegalArgumentException(
                "subBlockTotalBytes 不能为负数: " + subBlockTotalBytes);
        }
        
        this.subBlockTotalBytes = subBlockTotalBytes;
        this.compressedBuffer = Objects.requireNonNull(compressedBuffer, "compressedBuffer 不能为 null");
    }
}