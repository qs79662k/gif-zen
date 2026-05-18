package org.wsp.zen.gif.model;

import java.util.Objects;

/**
 * GIF 渲染上下文，封装渲染一帧所需的所有输入数据。
 * <p>
 * 该类作为渲染器（{@link org.wsp.zen.gif.core.Renderer}）的入参，包含：
 * <ul>
 *   <li>当前帧的解码数据（像素索引 + 颜色表）</li>
 *   <li>当前帧的元数据（尺寸、偏移、处置方法等）</li>
 *   <li>可选的参考帧（前一帧渲染结果，用于合成）</li>
 *   <li>可选的背景色（ARGB 格式）</li>
 *   <li>可选的前一帧元数据（用于确定背景恢复行为）</li>
 * </ul>
 * 实例通过内部 {@link Builder} 创建，必需字段在构造时校验。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * @param <T> 渲染结果的图像类型（例如 {@link java.awt.image.BufferedImage}）
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.gif.core.Renderer
 * @see DecodeFrame
 * @see FrameInfo
 */
public final class RenderContext<T> {

    // ==================== 必需参数 ====================

    /** 当前帧的解码数据，包含像素索引数组和颜色表 */
    public final DecodeFrame decodeFrame;

    /** 当前帧的元数据（尺寸、偏移、延迟、处置方法等） */
    public final FrameInfo currentFrameInfo;

    // ==================== 可选参数 ====================

    /** 参考帧（通常为前一帧渲染后的完整画布），用于合成当前帧，可能为 {@code null} */
    public final T referenceFrame;

    /** 背景色（ARGB 格式），{@code -1} 表示未设置 */
    public final int backgroundColor;

    /** 前一帧的元数据，用于确定背景恢复行为，可能为 {@code null} */
    public final FrameInfo prevFrameInfo;

    // ==================== 构造器 ====================

    /**
     * 私有构造器，通过 Builder 创建实例。
     *
     * @param builder 已配置的构建器实例
     * @throws NullPointerException 如果 {@code decodeFrame} 或 {@code currentFrameInfo} 为 {@code null}
     */
    private RenderContext(Builder<T> builder) {
        // 必须参数校验
        this.decodeFrame = Objects.requireNonNull(builder.decodeFrame, "当前帧解码数据不能为 null");
        this.currentFrameInfo = Objects.requireNonNull(builder.currentFrameInfo, "当前帧元数据不能为 null");

        // 可选参数
        this.referenceFrame = builder.referenceFrame;
        this.backgroundColor = builder.backgroundColor;
        this.prevFrameInfo = builder.prevFrameInfo;
    }

    // ==================== 便捷判断方法 ====================

    /**
     * 判断是否有参考帧。
     *
     * @return {@code true} 如果参考帧不为 {@code null}
     */
    public boolean hasReferenceFrame() {
        return referenceFrame != null;
    }

    /**
     * 判断是否设置了背景色。
     *
     * @return {@code true} 如果背景色不为 {@code -1}
     */
    public boolean hasBackgroundColor() {
        return backgroundColor != -1;
    }

    /**
     * 判断是否有前一帧元数据。
     *
     * @return {@code true} 如果前一帧元数据不为 {@code null}
     */
    public boolean hasPrevFrameInfo() {
        return prevFrameInfo != null;
    }

    // ==================== 内部 Builder ====================

    /**
     * {@link RenderContext} 的建造者类。
     * <p>
     * 提供流式 API 用于逐项设置渲染上下文参数，最终调用 {@link #build()} 生成不可变实例。
     * {@code decodeFrame} 和 {@code currentFrameInfo} 为必需字段。
     * </p>
     *
     * @param <T> 渲染结果类型
     */
    public static class Builder<T> {
        // 必须参数
        private DecodeFrame decodeFrame;
        private FrameInfo currentFrameInfo;

        // 可选参数（提供默认值）
        private T referenceFrame = null;
        private int backgroundColor = -1;
        private FrameInfo prevFrameInfo = null;

        /**
         * 设置当前帧的解码数据（必需）。
         *
         * @param decodeFrame 解码后的帧数据，不能为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder<T> withDecodeFrame(DecodeFrame decodeFrame) {
            this.decodeFrame = decodeFrame;
            return this;
        }

        /**
         * 设置当前帧的元数据（必需）。
         *
         * @param frameInfo 帧元数据，不能为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder<T> withCurrentFrameInfo(FrameInfo frameInfo) {
            this.currentFrameInfo = frameInfo;
            return this;
        }

        /**
         * 设置参考帧（可选）。
         *
         * @param referenceFrame 参考帧图像，可为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder<T> withReferenceFrame(T referenceFrame) {
            this.referenceFrame = referenceFrame;
            return this;
        }

        /**
         * 设置背景色（可选）。
         *
         * @param backgroundColor ARGB 格式的背景色，默认 {@code -1}
         * @return 当前 Builder 实例
         */
        public Builder<T> withBackgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        /**
         * 设置前一帧元数据（可选）。
         *
         * @param prevFrameInfo 前一帧信息，可为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder<T> withPrevFrameInfo(FrameInfo prevFrameInfo) {
            this.prevFrameInfo = prevFrameInfo;
            return this;
        }

        /**
         * 构建 {@link RenderContext} 实例。
         *
         * @return 新创建的不可变渲染上下文对象
         * @throws NullPointerException 如果 {@code decodeFrame} 或 {@code currentFrameInfo} 未设置
         */
        public RenderContext<T> build() {
            return new RenderContext<>(this);
        }
    }
}