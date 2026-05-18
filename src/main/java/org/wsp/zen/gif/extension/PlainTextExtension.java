package org.wsp.zen.gif.extension;

/**
 * GIF 纯文本扩展块（Plain Text Extension）的实现类。
 * <p>
 * 纯文本扩展块用于在 GIF 图形上叠加渲染文本，允许指定文本框位置、尺寸、
 * 字符单元格大小以及前景色/背景色索引。该类提供了不可变的数据结构，
 * 并通过内部 {@link Builder} 类支持灵活构造。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * GifPlainTextExtension textExt = new GifPlainTextExtension.Builder()
 *         .withTextGridPosition((short) 10, (short) 20)
 *         .withTextGridSize((short) 100, (short) 50)
 *         .withCharCellSize((byte) 8, (byte) 8)
 *         .withTextForegroundColorIndex((byte) 1)
 *         .withTextBackgroundColorIndex((byte) 0)
 *         .withPlainText("Hello GIF".getBytes(StandardCharsets.US_ASCII))
 *         .build();
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see Extension
 * @see ExtensionContainer
 */
public class PlainTextExtension implements Extension {

    /** 文本框左上角 X 坐标（相对于逻辑屏幕） */
    private final short textGridPositionX;

    /** 文本框左上角 Y 坐标（相对于逻辑屏幕） */
    private final short textGridPositionY;

    /** 文本框宽度（像素） */
    private final short textGridWidth;

    /** 文本框高度（像素） */
    private final short textGridHeight;

    /** 字符单元格宽度（像素） */
    private final byte charCellWidth;

    /** 字符单元格高度（像素） */
    private final byte charCellHeight;

    /** 文本前景色索引（指向全局或局部调色板） */
    private final byte textForegroundColorIndex;

    /** 文本背景色索引（指向全局或局部调色板） */
    private final byte textBackgroundColorIndex;

    /** 纯文本数据（已克隆，防御性复制） */
    private final byte[] plainText;

    /**
     * 私有构造方法，通过 {@link Builder} 创建实例。
     *
     * @param textGridPositionX         文本框左上角 X 坐标
     * @param textGridPositionY         文本框左上角 Y 坐标
     * @param textGridWidth             文本框宽度
     * @param textGridHeight            文本框高度
     * @param charCellWidth             字符单元格宽度
     * @param charCellHeight            字符单元格高度
     * @param textForegroundColorIndex  文本前景色索引
     * @param textBackgroundColorIndex  文本背景色索引
     * @param plainText                 纯文本数据
     */
    private PlainTextExtension(
            short textGridPositionX, short textGridPositionY, short textGridWidth, short textGridHeight,
            byte charCellWidth, byte charCellHeight,
            byte textForegroundColorIndex, byte textBackgroundColorIndex, byte[] plainText
    ) {
        this.textGridPositionX = textGridPositionX;
        this.textGridPositionY = textGridPositionY;
        this.textGridWidth = textGridWidth;
        this.textGridHeight = textGridHeight;
        this.charCellWidth = charCellWidth;
        this.charCellHeight = charCellHeight;
        this.textForegroundColorIndex = textForegroundColorIndex;
        this.textBackgroundColorIndex = textBackgroundColorIndex;
        this.plainText = plainText != null ? plainText.clone() : null;
    }

    /**
     * 获取文本框左上角 X 坐标（无符号整数）。
     *
     * @return X 坐标值，范围 0-65535
     */
    public int getTextGridPositionX() {
        return textGridPositionX & 0xffff;
    }

    /**
     * 获取文本框左上角 Y 坐标（无符号整数）。
     *
     * @return Y 坐标值，范围 0-65535
     */
    public int getTextGridPositionY() {
        return textGridPositionY & 0xffff;
    }

    /**
     * 获取文本框宽度（无符号整数）。
     *
     * @return 宽度值，范围 0-65535
     */
    public int getTextGridWidth() {
        return textGridWidth & 0xffff;
    }

    /**
     * 获取文本框高度（无符号整数）。
     *
     * @return 高度值，范围 0-65535
     */
    public int getTextGridHeight() {
        return textGridHeight & 0xffff;
    }

    /**
     * 获取字符单元格宽度（无符号整数）。
     *
     * @return 宽度值，范围 0-255
     */
    public int getCharCellWidth() {
        return charCellWidth & 0xff;
    }

    /**
     * 获取字符单元格高度（无符号整数）。
     *
     * @return 高度值，范围 0-255
     */
    public int getCharCellHeight() {
        return charCellHeight & 0xff;
    }

    /**
     * 获取文本前景色索引（无符号整数）。
     *
     * @return 调色板索引，范围 0-255
     */
    public int getTextForegroundColorIndex() {
        return textForegroundColorIndex & 0xff;
    }

    /**
     * 获取文本背景色索引（无符号整数）。
     *
     * @return 调色板索引，范围 0-255
     */
    public int getTextBackgroundColorIndex() {
        return textBackgroundColorIndex & 0xff;
    }

    /**
     * 返回纯文本数据（防御性副本）。
     * <p>
     * 返回的字节数组是内部数据的克隆，修改返回值不会影响当前对象。
     * </p>
     *
     * @return 纯文本数据的克隆，可能为 {@code null}
     */
    public byte[] getPlainText() {
        return plainText != null ? plainText.clone() : null;
    }

    /**
     * 接受一个扩展容器，将当前纯文本扩展添加到容器中。
     * <p>
     * 这是访问者模式的一部分，用于将扩展块分派到对应的容器槽位。
     * </p>
     *
     * @param extensionContainer 扩展容器，不能为 {@code null}
     */
    @Override
    public void applyTo(ExtensionContainer extensionContainer) {
        extensionContainer.addPlainTextExtension(this);
    }

    /**
     * {@link PlainTextExtension} 的建造者类。
     * <p>
     * 提供流式 API 用于逐项设置纯文本扩展块的各项参数，最终调用 {@link #build()} 生成不可变实例。
     * 所有必要字段在构建时进行非空校验，缺失将抛出 {@link IllegalStateException}。
     * </p>
     */
    public static class Builder {
        private Short textGridPositionX, textGridPositionY;
        private Short textGridWidth, textGridHeight;
        private Byte charCellWidth, charCellHeight;
        private Byte textForegroundColorIndex;
        private Byte textBackgroundColorIndex;
        private byte[] plainText;

        /**
         * 设置文本框左上角位置。
         *
         * @param x X 坐标（short 类型，将被视为无符号）
         * @param y Y 坐标（short 类型，将被视为无符号）
         * @return 当前 Builder 实例
         */
        public Builder withTextGridPosition(short x, short y) {
            this.textGridPositionX = x;
            this.textGridPositionY = y;
            return this;
        }

        /**
         * 设置文本框尺寸。
         *
         * @param width  宽度（short 类型，将被视为无符号）
         * @param height 高度（short 类型，将被视为无符号）
         * @return 当前 Builder 实例
         */
        public Builder withTextGridSize(short width, short height) {
            this.textGridWidth = width;
            this.textGridHeight = height;
            return this;
        }

        /**
         * 设置字符单元格尺寸。
         *
         * @param width  单元格宽度（byte 类型，将被视为无符号）
         * @param height 单元格高度（byte 类型，将被视为无符号）
         * @return 当前 Builder 实例
         */
        public Builder withCharCellSize(byte width, byte height) {
            this.charCellWidth = width;
            this.charCellHeight = height;
            return this;
        }

        /**
         * 设置文本前景色索引。
         *
         * @param index 调色板索引（byte 类型，将被视为无符号）
         * @return 当前 Builder 实例
         */
        public Builder withTextForegroundColorIndex(byte index) {
            this.textForegroundColorIndex = index;
            return this;
        }

        /**
         * 设置文本背景色索引。
         *
         * @param index 调色板索引（byte 类型，将被视为无符号）
         * @return 当前 Builder 实例
         */
        public Builder withTextBackgroundColorIndex(byte index) {
            this.textBackgroundColorIndex = index;
            return this;
        }

        /**
         * 设置纯文本数据。
         *
         * @param plainText 文本字节数组（通常为 ASCII 编码）
         * @return 当前 Builder 实例
         */
        public Builder withPlainText(byte[] plainText) {
            this.plainText = plainText;
            return this;
        }

        /**
         * 构建 {@link PlainTextExtension} 实例。
         *
         * @return 新创建的不可变实例
         * @throws IllegalStateException 如果任何必要字段未设置（文本框位置、尺寸、单元格尺寸、颜色索引）
         */
        public PlainTextExtension build() {
            if (textGridPositionX == null || textGridPositionY == null) {
                throw new IllegalStateException("未设置文本框位置");
            }
            if (textGridWidth == null || textGridHeight == null) {
                throw new IllegalStateException("未设置文本框尺寸");
            }
            if (charCellWidth == null || charCellHeight == null) {
                throw new IllegalStateException("未设置字符单元格尺寸");
            }
            if (textForegroundColorIndex == null) {
                throw new IllegalStateException("未设置文本前景色索引");
            }
            if (textBackgroundColorIndex == null) {
                throw new IllegalStateException("未设置文本背景色索引");
            }

            return new PlainTextExtension(
                    textGridPositionX, textGridPositionY,
                    textGridWidth, textGridHeight,
                    charCellWidth, charCellHeight,
                    textForegroundColorIndex, textBackgroundColorIndex,
                    plainText);
        }
    }
}