package org.wsp.zen.handler.model;

import java.util.Objects;

import org.wsp.zen.gif.model.DisplayMode;
import org.wsp.zen.gif.model.FrameInfo;
import org.wsp.zen.mapping.model.MappingContext;

/**
 * 处理器上下文对象，用于在 GIF 处理流水线的各阶段之间传递共享信息。
 * <p>
 * 该对象封装了处理一帧所需的所有必要参数和可选参数，包括当前帧索引、帧元数据、
 * 内存映射请求、全局颜色表、参考帧信息以及背景色索引等。不同阶段的处理器
 * （读取、解码、渲染）可根据需要从中获取相应的数据。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * HandlerContext context = new HandlerContext.Builder()
 *         .withCurrentFrameIndex(5)
 *         .withCurrentFrameInfo(frameInfo)
 *         .withGlobalColorTable(globalPalette)
 *         .withReferenceFrameIndex(4)
 *         .withBackgroundColorIndex(0)
 *         .build();
 *
 * int bgColor = context.getBackgroundColor();
 * DisplayMode prevMode = context.getPrevDisplayMode();
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see FrameInfo
 * @see MappingContext
 */
public final class HandlerContext {

    // ==================== 必需参数 ====================

    /** 当前帧索引，用作缓存键值 */
    public final int currentFrameIndex;

    /** 当前帧的元数据信息（尺寸、偏移、延迟、颜色表等） */
    public final FrameInfo currentFrameInfo;

    /** 内存映射请求上下文，用于指导文件映射管理器的工作方式，可为 {@code null} */
    public final MappingContext mappingContext;

    /** 全局颜色表（ARGB 格式），可能为 {@code null} 或空数组 */
    public final int[] globalColorTable;

    // ==================== 可选参数 ====================

    /** 前一帧的元数据信息，用于渲染阶段的背景恢复，可为 {@code null} */
    public final FrameInfo prevFrameInfo;

    /** 参考帧索引，用于指定渲染时应作为底层的帧，{@code -1} 表示无参考帧 */
    public final int referenceFrameIndex;

    /** 背景色索引，指向全局颜色表中的某个条目，{@code -1} 表示未设置 */
    public final int backgroundColorIndex;

    // ==================== 构造器 ====================

    /**
     * 私有构造方法，通过 {@link Builder} 创建实例。
     *
     * @param builder 已配置的构建器实例
     * @throws NullPointerException 如果 {@code builder.currentFrameInfo} 为 {@code null}
     * @throws IllegalArgumentException 如果帧索引为负数，或参考帧索引不小于当前帧索引
     */
    private HandlerContext(Builder builder) {
        // 核心参数
        this.globalColorTable = builder.globalColorTable;
        this.currentFrameIndex = builder.currentFrameIndex;
        this.mappingContext = builder.mappingContext;
        this.currentFrameInfo = Objects.requireNonNull(builder.currentFrameInfo, "当前帧元数据不能为 null");

        // 可选参数
        this.prevFrameInfo = builder.prevFrameInfo;
        this.referenceFrameIndex = builder.referenceFrameIndex;
        this.backgroundColorIndex = builder.backgroundColorIndex;

        // 帧索引非负校验
        if (currentFrameIndex < 0) {
            throw new IllegalArgumentException("当前帧索引必须为非负数，实际值：" + currentFrameIndex);
        }

        // 验证参考帧索引的有效性（如果提供了该参数）
        if (referenceFrameIndex != -1 && referenceFrameIndex >= currentFrameIndex) {
            throw new IllegalArgumentException(
                "参考帧索引必须小于当前帧索引，当前帧：" + currentFrameIndex +
                "，参考帧：" + referenceFrameIndex);
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断当前帧是否有参考帧。
     *
     * @return {@code true} 如果参考帧索引不为 -1
     */
    public boolean hasReferenceFrame() {
        return referenceFrameIndex != -1;
    }

    /**
     * 获取前一帧的处置方法（Disposal Method）。
     *
     * @return 前一帧的 {@link DisplayMode}，如果前一帧不存在则返回 {@code null}
     */
    public DisplayMode getPrevDisplayMode() {
        return prevFrameInfo != null ? prevFrameInfo.getDisplayMode() : null;
    }

    /**
     * 获取背景色（ARGB 格式）。
     * <p>
     * 遵循 GIF 规范处理逻辑：
     * <ol>
     *   <li>若全局颜色表不存在或为空，则返回默认背景色（白色：{@code 0xFFFFFF}）</li>
     *   <li>若全局颜色表存在，但背景色索引超出范围（0-255 或超出颜色表长度），则抛出异常</li>
     *   <li>否则返回全局颜色表中对应索引的颜色值</li>
     * </ol>
     * </p>
     *
     * @return ARGB 格式的背景色，全局颜色表不存在时返回默认白色
     * @throws IllegalStateException 全局颜色表存在但背景色索引无效时抛出
     */
    public int getBackgroundColor() {
        // 1. 全局颜色表不存在/为空时，按 GIF 规范返回默认背景色
        if (globalColorTable == null || globalColorTable.length == 0) {
            return 0xffffff;
        }

        // 2. 全局颜色表存在时，校验背景色索引的有效性
        if (backgroundColorIndex < 0 || backgroundColorIndex >= 256) {
            throw new IllegalStateException(
                "背景色索引超出GIF规范范围（0-255），实际值：" + backgroundColorIndex);
        }

        if (backgroundColorIndex >= globalColorTable.length) {
            throw new IllegalStateException(
                "背景色索引超出全局颜色表范围，索引：" + backgroundColorIndex +
                "，颜色表实际大小：" + globalColorTable.length);
        }

        // 3. 索引有效时返回对应颜色
        return globalColorTable[backgroundColorIndex];
    }

    // ==================== 内部 Builder ====================

    /**
     * {@link HandlerContext} 的建造者类。
     * <p>
     * 提供流式 API 用于逐项设置上下文参数，最终调用 {@link #build()} 生成不可变实例。
     * </p>
     */
    public static class Builder {
        // 必须参数
        private int currentFrameIndex;
        private FrameInfo currentFrameInfo;
        private MappingContext mappingContext;
        private int[] globalColorTable;

        // 可选参数（提供默认值）
        private FrameInfo prevFrameInfo = null;
        private int referenceFrameIndex = -1;
        private int backgroundColorIndex = -1;

        /**
         * 设置当前帧索引。
         *
         * @param currentFrameIndex 帧索引，必须 ≥ 0
         * @return 当前 Builder 实例
         */
        public Builder withCurrentFrameIndex(int currentFrameIndex) {
            this.currentFrameIndex = currentFrameIndex;
            return this;
        }

        /**
         * 设置当前帧元数据（必需）。
         *
         * @param currentFrameInfo 帧元数据，不能为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder withCurrentFrameInfo(FrameInfo currentFrameInfo) {
            this.currentFrameInfo = currentFrameInfo;
            return this;
        }

        /**
         * 设置内存映射上下文。
         *
         * @param mappingContext 映射上下文，可为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder withMappingContext(MappingContext mappingContext) {
            this.mappingContext = mappingContext;
            return this;
        }

        /**
         * 设置全局颜色表。
         *
         * @param globalColorTable ARGB 颜色数组，可为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder withGlobalColorTable(int[] globalColorTable) {
            this.globalColorTable = globalColorTable;
            return this;
        }

        /**
         * 设置前一帧元数据。
         *
         * @param prevFrameInfo 前一帧信息，可为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder withPrevFrameInfo(FrameInfo prevFrameInfo) {
            this.prevFrameInfo = prevFrameInfo;
            return this;
        }

        /**
         * 设置参考帧索引。
         *
         * @param referenceFrameIndex 参考帧索引，{@code -1} 表示无参考帧
         * @return 当前 Builder 实例
         */
        public Builder withReferenceFrameIndex(int referenceFrameIndex) {
            this.referenceFrameIndex = referenceFrameIndex;
            return this;
        }

        /**
         * 设置背景色索引。
         *
         * @param backgroundColorIndex 调色板索引，{@code -1} 表示未设置
         * @return 当前 Builder 实例
         */
        public Builder withBackgroundColorIndex(int backgroundColorIndex) {
            this.backgroundColorIndex = backgroundColorIndex;
            return this;
        }

        /**
         * 构建 {@link HandlerContext} 实例。
         *
         * @return 新创建的不可变上下文对象
         * @throws NullPointerException 如果 {@code currentFrameInfo} 未设置
         * @throws IllegalArgumentException 如果帧索引或参考帧索引不合法
         */
        public HandlerContext build() {
            return new HandlerContext(this);
        }
    }
}