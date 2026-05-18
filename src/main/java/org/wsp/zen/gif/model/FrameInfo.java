package org.wsp.zen.gif.model;

import java.util.Objects;

import org.wsp.zen.gif.extension.GraphicsControlExtension;

/**
 * GIF 帧元数据。
 * <p>
 * 该类封装了 GIF 单帧的所有元信息，包括图形控制扩展（延迟时间、透明色、处置方法等）、
 * 图像描述符（尺寸、位置、交错标志、LZW 最小码长等）以及帧数据在文件中的位置信息。
 * 所有字段均为不可变，通过构造函数一次性初始化。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * GraphicsControlExtension gce = ...;
 * ImageDescriptor descriptor = ...;
 * FrameInfo frameInfo = new FrameInfo(gce, descriptor);
 *
 * int delayMs = frameInfo.getDelayTime();        // 获取延迟时间（毫秒）
 * boolean isTransparent = frameInfo.isTransparentColor();
 * int transparentIndex = frameInfo.getTransparentColorIndex();
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see GraphicsControlExtension
 * @see ImageDescriptor
 * @see DisplayMode
 */
public final class FrameInfo {

    // ==================== 图形控制扩展相关字段 ====================

    /** 打包的控制标志位（包含处置方法、用户输入标志、透明色标志） */
    private final byte packedControlFlags;

    /** 下一帧延迟时间（单位：1/100 秒） */
    private final short delayTime;

    /** 透明色索引（仅在透明色标志位为 1 时有效） */
    private final byte transparentColorIndex;

    // ==================== 图像描述符相关字段 ====================

    /** 打包的描述符字段（包含局部颜色表标志、交错标志、颜色表大小等） */
    private final byte packedDescriptorField;

    /** 帧在逻辑屏幕上的左上角 X 偏移 */
    private final short offsetX;

    /** 帧在逻辑屏幕上的左上角 Y 偏移 */
    private final short offsetY;

    /** 帧宽度（像素） */
    private final short width;

    /** 帧高度（像素） */
    private final short height;

    /** LZW 最小码长（范围 2-12，通常为 8） */
    private final byte minCodeSize;

    // ==================== 帧数据存储信息 ====================

    /** 压缩数据在文件中的起始偏移量（不含局部颜色表），-1 表示未知或不适用 */
    private final long dataOffset;

    /** 压缩数据的字节数（包含子块长度标记） */
    private final int dataSize;

    /** 局部颜色表（ARGB 格式），可能为 {@code null}（表示使用全局颜色表） */
    private final int[] localColorTable;

    /** 压缩数据内容，可能为 {@code null}（表示数据未加载到内存，需通过偏移量从文件读取） */
    private final byte[] compressedBuffer;

    /** 该帧的关键帧索引，用于渲染优化 */
    private final int keyframeIndex;

    // ==================== 构造器 ====================

    /**
     * 构造函数，从图形控制扩展和图像描述符中构建帧信息。
     * <p>
     * 如果 {@code graphicsControl} 为 {@code null}，则使用默认值：
     * <ul>
     *   <li>控制标志位 = 0x00</li>
     *   <li>延迟时间 = 0</li>
     *   <li>透明色索引 = 0</li>
     * </ul>
     * </p>
     *
     * @param graphicsControl 图形控制扩展（可为 {@code null}，此时使用默认值）
     * @param imageDescriptor 图像描述符，不能为 {@code null}
     * @throws NullPointerException 如果 {@code imageDescriptor} 为 {@code null}
     */
    public FrameInfo(GraphicsControlExtension graphicsControl, ImageDescriptor imageDescriptor) {
        Objects.requireNonNull(imageDescriptor, "图像描述符不能为 null");

        // 处理图形控制扩展
        if (graphicsControl == null) {
            this.packedControlFlags = 0x00;
            this.delayTime = 0;
            this.transparentColorIndex = -1;
        } else {
            this.packedControlFlags = graphicsControl.getPackedField();
            this.delayTime = graphicsControl.getDelayTime();
            this.transparentColorIndex = graphicsControl.getTransparentColorIndex();
        }

        // 从图像描述符提取数据
        this.packedDescriptorField = imageDescriptor.getPackedField();
        this.offsetX = imageDescriptor.getOffsetX();
        this.offsetY = imageDescriptor.getOffsetY();
        this.width = imageDescriptor.getWidth();
        this.height = imageDescriptor.getHeight();
        this.minCodeSize = imageDescriptor.getLzwMinCodeSize();
        this.dataOffset = imageDescriptor.getDataOffset();
        this.dataSize = imageDescriptor.getDataSize();
        this.localColorTable = imageDescriptor.getLocalColorTable();
        this.compressedBuffer = imageDescriptor.getCompressedBuffer();
        this.keyframeIndex = imageDescriptor.getKeyframeIndex();
    }

    // ==================== 显示模式与透明色 ====================

    /**
     * 获取该帧的处置方法（Disposal Method）。
     *
     * @return 处置方法枚举，若原始编码无效则返回 {@link DisplayMode#UNSPECIFIED}
     */
    public DisplayMode getDisplayMode() {
        int disposalMethod = (packedControlFlags & 0x1C) >> 2;
        return DisplayMode.fromCode(disposalMethod > 3 ? 0 : disposalMethod);
    }

    /**
     * 判断该帧是否需要用户输入。
     *
     * @return {@code true} 如果需要用户输入，否则 {@code false}
     */
    public boolean isRequiresUserInput() {
        return ((packedControlFlags & 0x02) >> 1) != 0;
    }

    /**
     * 判断该帧是否使用透明色。
     *
     * @return {@code true} 如果透明色标志位为 1，否则 {@code false}
     */
    public boolean isTransparentColor() {
        return (packedControlFlags & 0x01) != 0;
    }

    /**
     * 获取该帧的延迟时间（单位：毫秒）。
     * <p>
     * GIF 标准中的延迟时间以 1/100 秒为单位，此方法返回转换后的毫秒值。
     * 若原始延迟为 0，则返回最小延迟值 10 毫秒（符合常见解码器行为）。
     * </p>
     *
     * @return 延迟时间（毫秒），≥ 10
     */
    public int getDelayTime() {
        // 最小延迟为 10 毫秒
        return Math.max((delayTime & 0xFFFF) * 10, 10);
    }

    /**
     * 获取该帧的透明色索引。
     *
     * @return 透明色索引（0-255），如果该帧不使用透明色则返回 -1
     */
    public int getTransparentColorIndex() {
        return isTransparentColor() ? (transparentColorIndex & 0xFF) : -1;
    }

    // ==================== 图像描述符访问器 ====================

    /**
     * 判断该帧是否包含局部颜色表。
     *
     * @return {@code true} 如果使用局部颜色表，否则 {@code false}
     */
    public boolean isLocalColorTable() {
        return (packedDescriptorField & 0x80) != 0;
    }

    /**
     * 判断该帧是否为交错存储模式。
     *
     * @return {@code true} 如果为交错模式，否则 {@code false}
     */
    public boolean isInterlaced() {
        return (packedDescriptorField & 0x40) != 0;
    }

    /**
     * 获取局部颜色表的大小（颜色条目数）。
     *
     * @return 颜色表大小，通常为 2、4、8、16、32、64、128 或 256
     */
    public int getLocalColorTableSize() {
        return 1 << ((packedDescriptorField & 0x07) + 1);
    }

    /**
     * 获取帧左上角 X 偏移（无符号整数）。
     *
     * @return X 偏移（像素），范围 0-65535
     */
    public int getOffsetX() {
        return offsetX & 0xFFFF;
    }

    /**
     * 获取帧左上角 Y 偏移（无符号整数）。
     *
     * @return Y 偏移（像素），范围 0-65535
     */
    public int getOffsetY() {
        return offsetY & 0xFFFF;
    }

    /**
     * 获取帧宽度（无符号整数）。
     *
     * @return 宽度（像素），范围 0-65535
     */
    public int getWidth() {
        return width & 0xFFFF;
    }

    /**
     * 获取帧高度（无符号整数）。
     *
     * @return 高度（像素），范围 0-65535
     */
    public int getHeight() {
        return height & 0xFFFF;
    }

    /**
     * 获取 LZW 最小码长（无符号整数）。
     *
     * @return 最小码长，范围 2-12（通常为 8）
     */
    public int getMinCodeSize() {
        return minCodeSize & 0xFF;
    }

    // ==================== 数据偏移与大小 ====================

    /**
     * 获取压缩数据的起始偏移（不含局部颜色表）。
     *
     * @return 压缩数据在文件中的偏移量，无效时返回 -1
     */
    public long getDataOffset() {
        return dataOffset < 0 ? -1 : dataOffset;
    }

    /**
     * 获取压缩数据的字节数（包含子块长度标记）。
     *
     * @return 压缩数据大小（字节）
     */
    public int getDataSize() {
        return dataSize;
    }

    /**
     * 获取局部颜色表的起始偏移（若存在）。
     * <p>
     * 局部颜色表紧邻在压缩数据块之前存储。
     * </p>
     *
     * @return 局部颜色表起始偏移，若不存在或无效返回 -1
     */
    public long getLocalColorTableOffset() {
        if (!isLocalColorTable()) return -1;
        long colorTableBytes = (long) getLocalColorTableSize() * 3;
        long offset = dataOffset - 1 - colorTableBytes;
        return offset < 0 ? -1 : offset;
    }

    /**
     * 获取整个帧数据（包括局部颜色表，若有）的起始偏移。
     *
     * @return 帧数据在文件中的起始偏移量
     */
    public long getFrameStartOffset() {
        long offset = getLocalColorTableOffset();
        return offset == -1 ? dataOffset : offset;
    }

    /**
     * 获取整个帧数据（包括局部颜色表，若有）的结束偏移。
     *
     * @return 帧数据在文件中的结束偏移量（包含）
     */
    public long getFrameEndOffset() {
        return dataOffset + dataSize - 1;
    }

    // ==================== 数据内容获取 ====================

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
     * @return 压缩数据内容，若为 {@code null} 则需要通过 {@link #getDataOffset()} 从文件读取
     */
    public byte[] getCompressedBuffer() {
        return compressedBuffer;
    }

    // ==================== 关键帧索引 ====================

    /**
     * 获取该帧对应的关键帧索引。
     * <p>
     * 关键帧是指可以独立渲染的完整帧（无需依赖前一帧内容）。
     * </p>
     *
     * @return 关键帧索引，始终 ≥ 0
     */
    public int getKeyframeIndex() {
        return keyframeIndex;
    }
}