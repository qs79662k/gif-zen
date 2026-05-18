package org.wsp.zen.gif.model;

/**
 * GIF 图形控制扩展中的处置方法（Disposal Method）枚举。
 * <p>
 * 处置方法定义了在当前帧显示完成后，如何处理画布上的内容以便为下一帧做准备。
 * 该枚举对应 GIF 规范中的 3 位处置方法字段（取值范围 0-3），用于指导渲染器
 * 正确地恢复背景或保留前一帧状态。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 枚举类型天然是线程安全的，可在多线程环境中安全使用。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * GraphicsControlExtension gce = ...;
 * DisplayMode mode = gce.getDisplayMode();
 * switch (mode) {
 *     case UNSPECIFIED:
 *         // 默认处理，通常等同于 DO_NOT_DISPOSE
 *         break;
 *     case DO_NOT_DISPOSE:
 *         // 保留当前叠加结果
 *         break;
 *     case RESTORE_BACKGROUND:
 *         // 将帧矩形区域恢复为背景色
 *         break;
 *     case RESTORE_PREVIOUS:
 *         // 恢复为当前帧绘制前的画布状态
 *         break;
 * }
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.gif.extension.GraphicsControlExtension
 */
public enum DisplayMode {

    /**
     * 未指定处理方式（代码 0）。
     * <p>
     * GIF 规范中此值表示未指定处置方法。大多数解码器将其视为“不处置”，
     * 即保留当前叠加结果。如果从无效代码转换而来，也返回此值作为默认。
     * </p>
     */
    UNSPECIFIED(0, "未指定处理方式"),

    /**
     * 不处置画布，保留当前帧叠加态（代码 1）。
     * <p>
     * 渲染后，画布保持当前所有已绘制内容不变，后续帧继续叠加在此之上。
     * 典型场景：逐帧累积绘制，如：
     * <pre>
     * 帧1: [A]
     * 帧2: [A+B]
     * 帧3: [A+B+C]
     * </pre>
     * </p>
     */
    DO_NOT_DISPOSE(1, "不处置画布，保留当前帧叠加态"),

    /**
     * 还原为背景色（代码 2）。
     * <p>
     * 渲染完成后，将当前帧所占据的矩形区域恢复为背景色。
     * 注意：仅恢复该矩形区域，而非整个画布。
     * 典型场景：
     * <pre>
     * 帧1: [A]
     * 帧2: [背景] -> [B]
     * 帧3: [背景] -> [C]
     * </pre>
     * </p>
     */
    RESTORE_BACKGROUND(2, "还原为背景"),

    /**
     * 还原为上一帧（代码 3）。
     * <p>
     * 渲染完成后，将画布恢复为当前帧绘制前的状态。
     * 这要求渲染器保留绘制前的画布副本。
     * 典型场景：
     * <pre>
     * 帧1: [A]
     * 帧2: [A] -> [临时覆盖X]（绘制后恢复为 A）
     * 帧3: [A]（继续基于 A 绘制）
     * </pre>
     * </p>
     */
    RESTORE_PREVIOUS(3, "还原为上一帧");

    /** 处置方法的原始编码（0-3） */
    private final int code;

    /** 处置方法的中文描述 */
    private final String description;

    /**
     * 私有构造器，初始化枚举常量。
     *
     * @param code        原始编码
     * @param description 中文描述
     */
    DisplayMode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取处置方法的原始编码。
     *
     * @return 编码值（0-3）
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取处置方法的中文描述。
     *
     * @return 描述字符串，不为 {@code null}
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据原始编码返回对应的枚举常量。
     * <p>
     * 如果传入的编码不在 0-3 范围内或未定义，则返回默认值 {@link #UNSPECIFIED}。
     * </p>
     *
     * @param code 原始编码（通常来自图形控制扩展的 packed 字段）
     * @return 对应的枚举常量，如果编码无效则返回 {@link #UNSPECIFIED}
     */
    public static DisplayMode fromCode(int code) {
        for (DisplayMode mode : DisplayMode.values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        // 无效值返回默认处理方式
        return UNSPECIFIED;
    }
}