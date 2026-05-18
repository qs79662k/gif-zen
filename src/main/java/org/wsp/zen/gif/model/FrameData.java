package org.wsp.zen.gif.model;

import java.util.Objects;

/**
 * GIF 原始帧数据容器，封装一帧的压缩像素数据及其对应的颜色表。
 * <p>
 * 该类用于在读取阶段存储从 GIF 文件中提取的原始压缩数据块（已去除子块长度标记）
 * 以及该帧实际使用的颜色表（优先局部颜色表，否则为全局颜色表）。
 * 后续将由解码处理器进行 LZW 解压。
 * </p>
 *
 * <p><b>资源管理设计：</b>
 * 当使用对象池管理内存时，帧的颜色表可能来自局部颜色表（每个帧独立拥有）或
 * 全局颜色表（多个帧共享同一个数组引用）。为了避免全局颜色表被错误归还到对象池
 * 而导致其他正在使用该颜色表的帧出现数据损坏（颜色错乱或花屏），
 * 通过 {@link #isLocalColorTable} 字段明确标识颜色表的来源：
 * <ul>
 *   <li>{@code true} — 局部颜色表，由本帧独占，帧数据被淘汰时可以安全归还给对象池。</li>
 *   <li>{@code false} — 全局颜色表，属于共享数据，<b>绝不能归还到对象池</b>，
 *       否则会破坏其他帧的正常渲染。该颜色表的生命周期由 GIF 头部（{@code Header}）管理。</li>
 * </ul>
 * 使用者在缓存淘汰监听器中应根据此字段决定是否将颜色表归还给对应的对象池。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.handler.core.Handler
 * @see org.wsp.zen.gif.core.LzwDecompressor
 */
public final class FrameData {

    /** 压缩的像素数据（已去除子块长度标记），用于 LZW 解压 */
    public final byte[] compressedBuffer;

    /** 该帧使用的颜色表（ARGB 格式），可能来自局部颜色表或全局颜色表 */
    public final int[] colorTable;

    /**
     * 标识该颜色表是否为局部颜色表（由当前帧独享）。
     * <ul>
     *   <li>{@code true} — 局部颜色表，帧被淘汰时可以归还对象池。</li>
     *   <li>{@code false} — 全局颜色表（来自 GIF 全局调色板），帧被淘汰时<b>不可归还</b>。</li>
     * </ul>
     */
    public final boolean isLocalColorTable;

    /**
     * 构造一个帧数据对象。
     *
     * @param compressedBuffer  压缩的像素数据，不能为 {@code null} 且长度必须大于 0
     * @param colorTable        颜色表数组（ARGB 格式），不能为 {@code null}
     * @param isLocalColorTable 颜色表是否为局部颜色表（{@code true} 表示局部），
     *                          用于对象池归还策略的判断
     * @throws NullPointerException     如果 {@code compressedBuffer} 或 {@code colorTable} 为 {@code null}
     * @throws IllegalArgumentException 如果 {@code compressedBuffer} 长度为 0
     */
    public FrameData(byte[] compressedBuffer, int[] colorTable, boolean isLocalColorTable) {
        this.compressedBuffer = Objects.requireNonNull(compressedBuffer, "压缩数据不能为 null");

        if (compressedBuffer.length == 0) {
            throw new IllegalArgumentException("压缩数据长度不能为 0");
        }

        this.colorTable = Objects.requireNonNull(colorTable, "颜色表不能为 null");
        this.isLocalColorTable = isLocalColorTable;
    }
}