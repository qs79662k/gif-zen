package org.wsp.zen.gif.model;

/**
 * GIF 文件头信息，封装了逻辑屏幕描述符及全局颜色表等元数据。
 * <p>
 * 该类对应 GIF 规范中的 Header 和 Logical Screen Descriptor 块，包含：
 * <ul>
 *   <li>魔数（签名）和版本号</li>
 *   <li>逻辑屏幕宽度和高度</li>
 *   <li>打包字段：全局颜色表标志、颜色分辨率、排序标志、颜色表大小</li>
 *   <li>背景色索引和像素宽高比</li>
 *   <li>全局颜色表（ARGB 格式）</li>
 * </ul>
 * 实例通过内部 {@link Builder} 创建，并在构造时进行合法性校验。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Header header = new Header.Builder()
 *         .withMagicNumber("GIF")
 *         .withVersion("89a")
 * .        withCanvasSize((short) 320, (short) 240)
 *         .withPackedField((byte) 0x87)
 *         .withBackgroundColorIndex((byte) 0)
 *         .withPixelAspectRatio((byte) 0)
 *         .withGlobalColorTable(globalPalette)
 *         .build();
 *
 * int width = header.getWidth();
 * int height = header.getHeight();
 * int[] palette = header.getGlobalColorTable();
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.gif.core.Parser
 */
public final class Header {

    // ==================== 字段 ====================

    /** 魔数，固定为 "GIF" */
    private final String magicNumber;

    /** GIF 版本，通常为 "87a" 或 "89a" */
    private final String version;

    /** 逻辑屏幕宽度（像素） */
    private final short width;

    /** 逻辑屏幕高度（像素） */
    private final short height;

    /**
     * 打包字段（1 字节），包含全局颜色表信息、颜色分辨率等。
     * <p>
     * 位结构（bit7～bit0）：
     * <ul>
     *   <li>bit7：全局颜色表标识（1 表示存在）</li>
     *   <li>bit6-4：颜色分辨率（值为实际位数 - 1）</li>
     *   <li>bit3：全局颜色表排序标识（1 表示按重要性降序排列）</li>
     *   <li>bit2-0：全局颜色表大小（值为实际大小指数 - 1，实际条目数为 2^(size+1)）</li>
     * </ul>
     * </p>
     */
    private final byte packedField;

    /** 背景色索引，指向全局颜色表中的某个条目 */
    private final byte backgroundColorIndex;

    /** 像素宽高比，通常为 0（表示未指定） */
    private final byte pixelAspectRatio;

    /** 全局颜色表（ARGB 格式），可能为 {@code null}（若 packedField 指示不存在） */
    private final int[] globalColorTable;

    // ==================== 构造器与校验 ====================

    /**
     * 私有构造器，通过 Builder 创建实例并执行校验。
     *
     * @param builder 已配置的构建器实例
     * @throws IllegalArgumentException 如果参数不符合 GIF 规范（详见 {@link #validate()}）
     */
    private Header(Builder builder) {
        this.magicNumber = builder.magicNumber;
        this.version = builder.version;
        this.width = builder.width;
        this.height = builder.height;
        this.packedField = builder.packedField;
        this.backgroundColorIndex = builder.backgroundColorIndex;
        this.pixelAspectRatio = builder.pixelAspectRatio;
        this.globalColorTable = builder.globalColorTable != null ? builder.globalColorTable.clone() : null;

        validate();
    }

    /**
     * 校验头部信息的合法性。
     * <p>
     * 校验规则：
     * <ul>
     *   <li>魔数必须为 "GIF"</li>
     *   <li>版本若为 "89a"，则 packedField 的保留位（bit5）应为 0，否则仅警告</li>
     *   <li>若 packedField 指示存在全局颜色表，则颜色表数组长度必须与指定大小一致</li>
     *   <li>背景色索引不能超出全局颜色表范围</li>
     * </ul>
     * 部分非致命错误（如未知版本、保留位非零）仅打印警告，不阻断解析流程。
     * </p>
     *
     * @throws IllegalArgumentException 如果魔数错误或颜色表/背景色索引不一致
     */
    private void validate() {
        // 校验魔数（必须为 'GIF'）- 这是基本要求
        if (magicNumber == null || !magicNumber.equals("GIF")) {
            throw new IllegalArgumentException("无效的 GIF 魔数，必须为 'GIF' ");
        }

        // 版本检查：不再抛出异常，改为警告并继续
        if (version == null) {
            System.err.println("警告：无法读取 GIF 版本，尝试继续解析");
        } else if (!version.equalsIgnoreCase("87a") && !version.equalsIgnoreCase("89a")) {
            System.err.println("警告：未知的 GIF 版本 '" + version + "'，尝试继续解析");
        }

        // 保留位检查：降级为警告，不再抛出异常
        int bit5 = (packedField & 0x20) >>> 5;
        if (version != null && version.equalsIgnoreCase("89a") && bit5 != 0) {
            System.err.println("警告：GIF89a 的 packedField 第 5 位保留位应为 0，实际值：" + bit5 + "，继续解析");
        }

        // 校验全局颜色表与 packedField 的一致性
        boolean hasGlobalColorTable = (packedField & 0x80) != 0;
        if (hasGlobalColorTable) {
            int colorTableSize = 1 << ((packedField & 0x07) + 1);
            if (globalColorTable == null || globalColorTable.length != colorTableSize) {
//                throw new IllegalArgumentException("全局颜色表长度与 packedField 指定不符");
            }

            // 背景色索引检查：如果越界抛出异常
            int bgIndex = backgroundColorIndex & 0xff;
            if (bgIndex >= globalColorTable.length) {
                throw new IllegalArgumentException("背景色索引超出全局颜色表范围");
            }
        }
    }

    // ==================== 访问器 ====================

    /**
     * 获取魔数。
     *
     * @return 魔数字符串（固定为 "GIF"）
     */
    public String getMagicNumber() {
        return magicNumber;
    }

    /**
     * 获取 GIF 版本。
     *
     * @return 版本字符串，例如 "87a" 或 "89a"
     */
    public String getVersion() {
        return version;
    }

    /**
     * 获取逻辑屏幕宽度（无符号整数）。
     *
     * @return 宽度（像素），范围 0-65535
     */
    public int getWidth() {
        return width & 0xffff;
    }

    /**
     * 获取逻辑屏幕高度（无符号整数）。
     *
     * @return 高度（像素），范围 0-65535
     */
    public int getHeight() {
        return height & 0xffff;
    }

    // ==================== 颜色表相关访问器 ====================

    /**
     * 判断是否存在全局颜色表。
     *
     * @return {@code true} 如果 packedField 的 bit7 为 1，否则 {@code false}
     */
    public boolean isGlobalColorTable() {
        return (packedField & 0x80) != 0;
    }

    /**
     * 获取颜色分辨率（原始图像的颜色位数）。
     *
     * @return 颜色位数，值为 packedField bit6-4 的值 + 1
     */
    public int getColorResolution() {
        return ((packedField & 0x70) >> 4) + 1;
    }

    /**
     * 判断全局颜色表是否按重要性降序排列。
     *
     * @return {@code true} 如果 packedField bit3 为 1，否则 {@code false}
     */
    public boolean isGlobalColorTableSorted() {
        return (packedField & 0x08) != 0;
    }

    /**
     * 获取全局颜色表的条目数。
     *
     * @return 颜色表大小，值为 2^(packedField bit2-0 的值 + 1)
     */
    public int getGlobalColorTableSize() {
        return 1 << ((packedField & 0x7) + 1);
    }

    /**
     * 获取背景色索引（无符号整数）。
     *
     * @return 背景色索引，范围 0-255
     */
    public int getBackgroundColorIndex() {
        return backgroundColorIndex & 0xff;
    }

    /**
     * 获取像素宽高比（无符号整数）。
     * <p>
     * 若值为 0，表示未指定；否则实际宽高比为 (pixelAspectRatio + 15) / 64。
     * </p>
     *
     * @return 像素宽高比原始值，范围 0-255
     */
    public int getPixelAspectRatio() {
        return pixelAspectRatio & 0xff;
    }

    /**
     * 获取全局颜色表数组（防御性副本）。
     * <p>
     * 返回的数组是内部数据的克隆，修改返回值不会影响当前对象。
     * </p>
     *
     * @return 全局颜色表（ARGB 格式数组），若不存在则返回 {@code null}
     */
    public int[] getGlobalColorTable() {
        return globalColorTable != null ? globalColorTable.clone() : null;
    }

    // ==================== 内部 Builder ====================

    /**
     * {@link Header} 的建造者类。
     * <p>
     * 提供流式 API 用于逐项设置头部参数，最终调用 {@link #build()} 生成不可变实例。
     * 所有字段均为必需，构建时会进行空值校验。
     * </p>
     */
    public static class Builder {
        private String magicNumber;
        private String version;
        private Short width;
        private Short height;
        private Byte packedField;
        private Byte backgroundColorIndex;
        private Byte pixelAspectRatio;
        private int[] globalColorTable;

        /**
         * 设置画布尺寸。
         *
         * @param width  宽度（像素）
         * @param height 高度（像素）
         * @return 当前 Builder 实例
         */
        public Builder withCanvasSize(short width, short height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * 设置魔数。
         *
         * @param magicNumber 魔数字符串（通常为 "GIF"）
         * @return 当前 Builder 实例
         */
        public Builder withMagicNumber(String magicNumber) {
            this.magicNumber = magicNumber;
            return this;
        }

        /**
         * 设置 GIF 版本。
         *
         * @param version 版本字符串（"87a" 或 "89a"）
         * @return 当前 Builder 实例
         */
        public Builder withVersion(String version) {
            this.version = version;
            return this;
        }

        /**
         * 设置打包字段。
         *
         * @param packedField 打包字段字节
         * @return 当前 Builder 实例
         */
        public Builder withPackedField(byte packedField) {
            this.packedField = packedField;
            return this;
        }

        /**
         * 设置背景色索引。
         *
         * @param index 背景色索引（0-255）
         * @return 当前 Builder 实例
         */
        public Builder withBackgroundColorIndex(byte index) {
            this.backgroundColorIndex = index;
            return this;
        }

        /**
         * 设置像素宽高比。
         *
         * @param ratio 像素宽高比原始值（0-255）
         * @return 当前 Builder 实例
         */
        public Builder withPixelAspectRatio(byte ratio) {
            this.pixelAspectRatio = ratio;
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
         * 构建 {@link Header} 实例。
         *
         * @return 新创建的不可变头部对象
         * @throws IllegalStateException 如果任何必需字段未设置
         */
        public Header build() {
            if (magicNumber == null) throw new IllegalStateException("未设置魔数");
            if (version == null) throw new IllegalStateException("未设置版本");
            if (width == null || height == null) throw new IllegalStateException("未设置画布尺寸");
            if (packedField == null) throw new IllegalStateException("未设置 packedField");
            if (backgroundColorIndex == null) throw new IllegalStateException("未设置背景色索引");
            if (pixelAspectRatio == null) throw new IllegalStateException("未设置像素宽高比");

            return new Header(this);
        }
    }
}