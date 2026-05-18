package org.wsp.zen.gif.model;

/**
 * GIF 图像描述符（Image Descriptor），描述一帧图像的几何信息、局部颜色表、压缩数据位置等。
 * <p>
 * 该类对应 GIF 规范中的 Image Descriptor 块，位于图形控制扩展之后，包含：
 * <ul>
 *   <li>帧在逻辑屏幕上的偏移位置（左上角坐标）</li>
 *   <li>帧的宽度和高度</li>
 *   <li>打包字段：局部颜色表标志、交错标志、排序标志、保留位、颜色表大小</li>
 *   <li>LZW 最小码长</li>
 *   <li>压缩数据在文件中的偏移量和大小</li>
 *   <li>局部颜色表（ARGB 格式）</li>
 *   <li>压缩数据内容（可能为 {@code null}，表示未加载到内存）</li>
 *   <li>该帧的关键帧索引</li>
 * </ul>
 * 实例通过内部 {@link Builder} 创建，并在构造时进行合法性校验。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see FrameInfo
 * @see org.wsp.zen.gif.core.Parser
 */
public final class ImageDescriptor {

    // ==================== 几何信息 ====================

    /** 帧在逻辑屏幕上的左上角 X 偏移 */
    private final short offsetX;

    /** 帧在逻辑屏幕上的左上角 Y 偏移 */
    private final short offsetY;

    /** 帧宽度（像素） */
    private final short width;

    /** 帧高度（像素） */
    private final short height;

    // ==================== 打包字段与编码信息 ====================

    /**
     * 打包控制标志位（1 字节）。
     * <p>
     * 位结构（bit7～bit0）：
     * <ul>
     *   <li>bit7：局部颜色表标志（1 表示存在）</li>
     *   <li>bit6：隔行扫描标志（1 表示交错模式）</li>
     *   <li>bit5：排序标志（1 表示按重要性降序排列）</li>
     *   <li>bit4-3：保留位，必须为 0</li>
     *   <li>bit2-0：局部颜色表大小（值为实际大小指数 - 1，实际条目数为 2^(size+1)）</li>
     * </ul>
     * </p>
     */
    private final byte packedField;

    /** LZW 压缩的最小码长（范围 2-12，通常为 8） */
    private final byte minCodeSize;

    // ==================== 数据存储位置 ====================

    /** 压缩数据在文件中的起始偏移量（不含局部颜色表），-1 表示未知或不适用 */
    private final long dataOffset;

    /** 压缩数据的字节数（包含子块长度标记） */
    private final int dataSize;

    // ==================== 数据内容 ====================

    /** 局部颜色表（ARGB 格式），可能为 {@code null}（表示使用全局颜色表） */
    private final int[] localColorTable;

    /** 压缩数据内容，可能为 {@code null}（表示数据未加载到内存，需通过偏移量从文件读取） */
    private final byte[] compressedBuffer;

    // ==================== 关键帧索引 ====================

    /** 该帧的关键帧索引，用于渲染优化 */
    private final int keyframeIndex;

    // ==================== 构造器与校验 ====================

    /**
     * 私有构造器，通过 Builder 创建实例并执行校验。
     *
     * @param offsetX         帧左上角 X 偏移
     * @param offsetY         帧左上角 Y 偏移
     * @param width           帧宽度
     * @param height          帧高度
     * @param packedField     打包字段
     * @param minCodeSize     LZW 最小码长
     * @param dataOffset      压缩数据偏移量
     * @param dataSize        压缩数据大小
     * @param localColorTable 局部颜色表
     * @param compressedBuffer 压缩数据内容，可为 {@code null}
     * @param keyframeIndex   关键帧索引
     * @throws IllegalArgumentException 如果参数不符合规范（详见 {@link #validate()}）
     */
    private ImageDescriptor(
            short offsetX, short offsetY, short width, short height,
            byte packedField, byte minCodeSize,
            long dataOffset, int dataSize,
            int[] localColorTable, byte[] compressedBuffer,
            int keyframeIndex) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = width;
        this.height = height;
        this.packedField = packedField;
        this.minCodeSize = minCodeSize;
        this.dataOffset = dataOffset;
        this.dataSize = dataSize;
        this.localColorTable = localColorTable;
        this.compressedBuffer = compressedBuffer;
        this.keyframeIndex = keyframeIndex;

        validate();
    }

    /**
     * 校验描述符信息的合法性。
     * <p>
     * 校验规则：
     * <ul>
     *   <li>保留位（bit4-3）必须为 0</li>
     *   <li>LZW 最小码长必须在 1~8 之间</li>
     *   <li>若存在局部颜色表，则颜色表数组长度必须与 packedField 指定大小一致</li>
     *   <li>若压缩数据为 {@code null}，则必须提供有效的偏移量和大小</li>
     *   <li>关键帧索引不能为负数</li>
     * </ul>
     * </p>
     *
     * @throws IllegalArgumentException 如果任何校验失败
     */
    private void validate() {
        // 保留位 (bit4~bit3) 必须为 0
        if ((packedField & 0x18) != 0) {
            throw new IllegalArgumentException(
                "图像描述符保留位必须为 0，实际：" + Integer.toBinaryString(packedField & 0xFF));
        }
        int codeSize = minCodeSize & 0xFF;
        if (codeSize < 1 || codeSize > 8) {
            throw new IllegalArgumentException("LZW 最小码长必须在 1~8 之间：" + codeSize);
        }
        boolean hasLocalColorTable = (packedField & 0x80) != 0;
        if (hasLocalColorTable) {
            int expectedSize = 1 << ((packedField & 0x07) + 1);
            if (localColorTable != null && localColorTable.length < expectedSize) {
                throw new IllegalArgumentException(
                    "局部颜色表大小不符，预期：" + expectedSize +
                    "，实际：" + (localColorTable == null ? 0 : localColorTable.length));
            }    
        }
        // 如果 compressedBuffer 为 null（流模式或大文件未加载），则必须依赖偏移和大小
        if (compressedBuffer == null) {
            if (dataOffset < 0) {
                throw new IllegalArgumentException("数据起始偏移不能为负数：" + dataOffset);
            }
            if (dataSize <= 0) {
                throw new IllegalArgumentException("数据大小必须大于 0：" + dataSize);
            }
        }
        if (keyframeIndex < 0) {
            throw new IllegalArgumentException("关键帧索引不能为负数：" + keyframeIndex);
        }
    }

    // ==================== 访问器 ====================

    /**
     * 获取帧左上角 X 偏移。
     *
     * @return X 偏移（像素），有符号 short 类型，调用方可自行处理无符号转换
     */
    public short getOffsetX() {
        return offsetX;
    }

    /**
     * 获取帧左上角 Y 偏移。
     *
     * @return Y 偏移（像素），有符号 short 类型
     */
    public short getOffsetY() {
        return offsetY;
    }

    /**
     * 获取帧宽度。
     *
     * @return 宽度（像素），有符号 short 类型
     */
    public short getWidth() {
        return width;
    }

    /**
     * 获取帧高度。
     *
     * @return 高度（像素），有符号 short 类型
     */
    public short getHeight() {
        return height;
    }

    /**
     * 获取打包字段的原始字节。
     *
     * @return 打包字段
     */
    public byte getPackedField() {
        return packedField;
    }

    /**
     * 获取 LZW 最小码长。
     *
     * @return 最小码长（范围 2-12）
     */
    public byte getLzwMinCodeSize() {
        return minCodeSize;
    }

    /**
     * 获取压缩数据在文件中的起始偏移量。
     *
     * @return 偏移量（字节），可能为 -1 表示未知
     */
    public long getDataOffset() {
        return dataOffset;
    }

    /**
     * 获取压缩数据的字节数（包含子块长度标记）。
     *
     * @return 数据大小（字节）
     */
    public int getDataSize() {
        return dataSize;
    }

    /**
     * 获取局部颜色表数组（可能为 {@code null}）。
     *
     * @return 局部颜色表（ARGB 格式数组），若无局部颜色表则返回 {@code null}
     */
    public int[] getLocalColorTable() {
        return localColorTable;
    }

    /**
     * 获取压缩后的帧数据字节数组（可能为 {@code null}，表示未加载到内存）。
     *
     * @return 压缩数据内容，若为 {@code null} 则需要通过偏移量从文件读取
     */
    public byte[] getCompressedBuffer() {
        return compressedBuffer;
    }

    /**
     * 获取该帧对应的关键帧索引。
     *
     * @return 关键帧索引，始终 ≥ 0
     */
    public int getKeyframeIndex() {
        return keyframeIndex;
    }

    // ==================== 内部 Builder ====================

    /**
     * {@link ImageDescriptor} 的建造者类。
     * <p>
     * 提供流式 API 用于逐项设置描述符参数，最终调用 {@link #build()} 生成不可变实例。
     * 偏移量、尺寸、打包字段、最小码长、数据偏移/大小、关键帧索引为必需字段。
     * </p>
     */
    public static class Builder {
        private Short offsetX;
        private Short offsetY;
        private Short width;
        private Short height;
        private Byte packedField;
        private Byte minCodeSize;
        private Long dataOffset;
        private Integer dataSize;
        private int[] localColorTable;
        private byte[] compressedBuffer;
        private Integer keyframeIndex;

        /**
         * 设置帧左上角偏移。
         *
         * @param x X 偏移
         * @param y Y 偏移
         * @return 当前 Builder 实例
         */
        public Builder withOffset(short x, short y) {
            this.offsetX = x;
            this.offsetY = y;
            return this;
        }

        /**
         * 设置帧尺寸。
         *
         * @param w 宽度
         * @param h 高度
         * @return 当前 Builder 实例
         */
        public Builder withSize(short w, short h) {
            this.width = w;
            this.height = h;
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
         * 设置 LZW 最小码长。
         *
         * @param minCodeSize 最小码长（范围 2-12）
         * @return 当前 Builder 实例
         */
        public Builder withMinCodeSize(byte minCodeSize) {
            this.minCodeSize = minCodeSize;
            return this;
        }

        /**
         * 设置压缩数据在文件中的偏移量和大小。
         *
         * @param offset 偏移量
         * @param size   数据大小
         * @return 当前 Builder 实例
         */
        public Builder withDataInfo(long offset, int size) {
            this.dataOffset = offset;
            this.dataSize = size;
            return this;
        }

        /**
         * 设置局部颜色表。
         *
         * @param table ARGB 颜色数组，可为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder withLocalColorTable(int[] table) {
            this.localColorTable = table;
            return this;
        }

        /**
         * 设置压缩数据内容。
         *
         * @param compressedBuffer 压缩数据，可为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder withCompressedBuffer(byte[] compressedBuffer) {
            this.compressedBuffer = compressedBuffer;
            return this;
        }

        /**
         * 设置关键帧索引。
         *
         * @param index 关键帧索引，必须 ≥ 0
         * @return 当前 Builder 实例
         */
        public Builder withKeyframeIndex(int index) {
            this.keyframeIndex = index;
            return this;
        }

        /**
         * 构建 {@link ImageDescriptor} 实例。
         *
         * @return 新创建的不可变图像描述符对象
         * @throws IllegalStateException 如果任何必需字段未设置
         */
        public ImageDescriptor build() {
            if (offsetX == null) throw new IllegalStateException("未设置 offsetX");
            if (offsetY == null) throw new IllegalStateException("未设置 offsetY");
            if (width == null) throw new IllegalStateException("未设置 width");
            if (height == null) throw new IllegalStateException("未设置 height");
            if (packedField == null) throw new IllegalStateException("未设置 packedField");
            if (minCodeSize == null) throw new IllegalStateException("未设置 minCodeSize");
            if (dataOffset == null) throw new IllegalStateException("未设置 dataOffset");
            if (dataSize == null) throw new IllegalStateException("未设置 dataSize");
            if (keyframeIndex == null) throw new IllegalStateException("未设置 keyframeIndex");
            return new ImageDescriptor(
                offsetX, offsetY, width, height,
                packedField, minCodeSize,
                dataOffset, dataSize,
                localColorTable, compressedBuffer,
                keyframeIndex);
        }
    }
}