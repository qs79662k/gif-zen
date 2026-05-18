package org.wsp.zen.gif.extension;

import org.wsp.zen.gif.model.DisplayMode;

/**
 * GIF 图形控制扩展块（Graphics Control Extension）的实现类。
 * <p>
 * 图形控制扩展块用于控制紧随其后的图像帧的显示方式，包括：
 * <ul>
 *   <li>帧间延迟时间（以 1/100 秒为单位）</li>
 *   <li>透明色索引（指定调色板中哪一颜色应视为透明）</li>
 *   <li>处置方法（Disposal Method），定义当前帧显示后如何处理前一帧内容</li>
 *   <li>用户输入标志（通常忽略）</li>
 * </ul>
 * 该扩展块是 GIF89a 规范的核心特性，用于实现动画效果。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * // 构造图形控制扩展：无处置、延迟 50 单位（0.5 秒）、透明色索引 0
 * byte packed = (byte) 0x04; // 处置方法 1（不处置），透明标志位 0
 * GraphicsControlExtension gce = new GraphicsControlExtension(packed, (short) 50, (byte) 0);
 *
 * int delayMs = gce.getDelayTime() * 10; // 转换为毫秒
 * boolean hasTransparency = gce.isTransparentColor();
 * DisplayMode mode = gce.getDisplayMode();
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see Extension
 * @see DisplayMode
 * @see ExtensionContainer
 */
public class GraphicsControlExtension implements Extension {

    /**
     * 打包控制标志位（1 字节）。
     * <p>
     * 位结构（bit7～bit0）：
     * <ul>
     *   <li>bit7-5：保留位，必须为 0</li>
     *   <li>bit4-2：处置方法（3 位）</li>
     *   <li>bit1：用户输入标志</li>
     *   <li>bit0：透明色标志</li>
     * </ul>
     * </p>
     */
    private final byte packedField;

    /**
     * 下一帧延迟时间，单位：1/100 秒。
     */
    private final short delayTime;

    /**
     * 透明色索引（指向全局或局部调色板），仅在透明标志位为 1 时有效。
     */
    private final byte transparentColorIndex;

    // ==================== 构造器与校验 ====================

    /**
     * 构造一个图形控制扩展块实例。
     * <p>
     * 构造过程中会进行格式校验：
     * <ul>
     *   <li>保留位必须为 0</li>
     *   <li>延迟时间不能为负数</li>
     *   <li>透明色索引必须在 0-255 范围内</li>
     * </ul>
     * </p>
     *
     * @param packedField           打包的控制标志位字节
     * @param delayTime             延迟时间，单位 1/100 秒，必须 ≥ 0
     * @param transparentColorIndex 透明色索引，范围 0-255
     * @throws IllegalArgumentException 如果任何参数不符合规范
     */
    public GraphicsControlExtension(byte packedField, short delayTime, byte transparentColorIndex) {
        this.packedField = packedField;
        this.delayTime = delayTime;
        this.transparentColorIndex = transparentColorIndex;
        validate();
    }

    /**
     * 内部校验方法，确保扩展块数据符合 GIF 规范。
     *
     * @throws IllegalArgumentException 如果保留位非零、延迟时间为负或透明索引超出范围
     */
    private void validate() {
        // 保留位必须为 0
        if ((packedField & 0xE0) != 0) {
            throw new IllegalArgumentException(
                "图形控制扩展的保留位必须为 0，实际：" + Integer.toBinaryString(packedField & 0xFF));
        }
        if (delayTime < 0) {
            throw new IllegalArgumentException("延迟时间不能为负数：" + delayTime);
        }
        int index = transparentColorIndex & 0xFF;
        if (index < 0 || index > 255) {
            throw new IllegalArgumentException("透明色索引必须在 0~255 之间：" + index);
        }
    }

    // ==================== 访问器 ====================

    /**
     * 获取打包的控制标志位字节。
     *
     * @return 原始打包字段
     */
    public byte getPackedField() {
        return packedField;
    }

    /**
     * 获取延迟时间（单位：1/100 秒）。
     * <p>
     * 注意：GIF 标准中的延迟时间以 1/100 秒（10 毫秒）为单位，转换为毫秒需乘以 10。
     * </p>
     *
     * @return 延迟时间，≥ 0
     */
    public short getDelayTime() {
        return delayTime;
    }

    /**
     * 获取透明色索引。
     * <p>
     * 仅在 {@link #isTransparentColor()} 返回 {@code true} 时有效。
     * </p>
     *
     * @return 透明色索引，范围 0-255
     */
    public byte getTransparentColorIndex() {
        return transparentColorIndex;
    }

    // ==================== 逻辑方法 ====================

    /**
     * 获取当前帧的处置方法（Disposal Method）。
     * <p>
     * 处置方法定义了在当前帧显示后，如何处理前一帧的图像内容：
     * <ul>
     *   <li>0 - 未指定（通常视为不处置）</li>
     *   <li>1 - 不处置（保留叠加结果）</li>
     *   <li>2 - 恢复为背景色</li>
     *   <li>3 - 恢复为先前状态</li>
     * </ul>
     * </p>
     *
     * @return 处置方法枚举，若原始编码无效则返回默认值 {@code DisplayMode.UNSPECIFIED}
     */
    public DisplayMode getDisplayMode() {
        int disposalMethod = (packedField & 0x1C) >> 2;
        return DisplayMode.fromCode(disposalMethod > 3 ? 0 : disposalMethod);
    }

    /**
     * 判断当前帧是否使用透明色。
     *
     * @return {@code true} 如果透明色标志位为 1，否则 {@code false}
     */
    public boolean isTransparentColor() {
        return (packedField & 0x01) != 0;
    }

    // ==================== 访问者模式分派 ====================

    /**
     * 接受一个扩展容器，将当前图形控制扩展设置到容器中。
     * <p>
     * 这是访问者模式的一部分，用于将扩展块分派到对应的容器槽位。
     * </p>
     *
     * @param container 扩展容器，不能为 {@code null}
     */
    @Override
    public void applyTo(ExtensionContainer container) {
        container.setGraphicsControlExtension(this);
    }
}