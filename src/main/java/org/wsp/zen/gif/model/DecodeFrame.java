package org.wsp.zen.gif.model;

/**
 * GIF 解码后的帧数据。
 * <p>
 * 该类封装了 LZW 解码后的像素索引数组以及对应的颜色表（ARGB 格式），
 * 用于后续的渲染阶段。解码成功与否由 {@code success} 字段标识。
 * </p>
 *
 * <p><b>资源管理设计：</b>
 * 与 {@link FrameData} 一致，通过 {@link #isLocalColorTable} 字段明确标识颜色表的来源：
 * <ul>
 *   <li>{@code true} — 局部颜色表，由本帧独占，帧数据被淘汰时可以安全归还给对象池。</li>
 *   <li>{@code false} — 全局颜色表，属于多帧共享的数据，<b>绝不能归还到对象池</b>，
 *       否则会破坏其他帧的正常渲染。该颜色表的生命周期由 GIF 头部（{@code Header}）管理。</li>
 * </ul>
 * 使用者在缓存淘汰监听器中应根据此字段决定是否将颜色表归还给对应的对象池。
 * </p>
 *
 * <p><b>注意：</b>
 * 像素索引数组和颜色表数组是直接引用，不会进行防御性拷贝。
 * 调用者不得修改这两个数组的内容，否则可能导致不可预期的渲染结果。
 * 若需要修改，请自行拷贝。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例在字段引用层面是不可变的（数组内容不在此列），因此是线程安全的，
 * 前提是调用者遵循上述不修改数组内容的约定。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.gif.core.LzwDecompressor
 * @see org.wsp.zen.gif.core.Renderer
 * @see FrameData
 */
public final class DecodeFrame {

    /** 解码是否成功（{@code true} 表示成功，且像素索引数组非空） */
    public final boolean success;

    /** 像素索引数组（每个字节为调色板索引），调用者不得修改 */
    public final byte[] pixelIndices;

    /** 颜色表（ARGB 格式数组），调用者不得修改 */
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
     * 构造一个解码后的帧数据对象。
     * <p>
     * 成功标志由像素索引数组是否为空决定：若数组为 {@code null} 或长度为 0，则视为解码失败。
     * </p>
     *
     * @param pixelIndices      像素索引数组，可能为 {@code null}（表示解码失败）
     * @param colorTable        颜色表数组，可能为 {@code null}
     * @param isLocalColorTable 颜色表是否为局部颜色表（{@code true} 表示局部），
     *                          用于对象池归还策略的判断
     */
    public DecodeFrame(byte[] pixelIndices, int[] colorTable, boolean isLocalColorTable) {
        this.pixelIndices = pixelIndices;
        this.colorTable = colorTable;
        this.isLocalColorTable = isLocalColorTable;
        this.success = !(pixelIndices == null || pixelIndices.length <= 0);
    }
}