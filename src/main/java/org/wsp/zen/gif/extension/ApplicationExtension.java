package org.wsp.zen.gif.extension;

import java.util.Objects;

/**
 * GIF 应用扩展块（Application Extension）的实现类。
 * <p>
 * 应用扩展块用于存储特定应用程序的私有数据，最常见的用途是 Netscape 扩展，
 * 用于指定 GIF 动画的循环次数。该扩展块由 11 字节的应用程序标识符（包括
 * 8 字节的应用名称和 3 字节的认证码）以及随后的数据子块组成。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * // 创建 NETSCAPE2.0 应用扩展（循环次数为 0 表示无限循环）
 * byte[] netscapeData = new byte[] {0x01, 0x00, 0x00}; // 子块数据：01 表示循环，后续两字节为循环次数
 * ApplicationExtension ext = new ApplicationExtension("NETSCAPE2.0", netscapeData);
 * int loopCount = ext.getLoopCount(); // 返回 0（无限循环）
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see Extension
 * @see org.wsp.zen.gif.extension.ExtensionContainer
 */
public class ApplicationExtension implements Extension {

    /**
     * 应用程序标识符，固定为 11 字节（例如 "NETSCAPE2.0"）。
     */
    private final String applicationInformation;

    /**
     * 应用扩展的数据块内容（已克隆，防御性复制）。
     */
    private final byte[] chunkData;

    // ==================== 构造器与校验 ====================

    /**
     * 构造一个应用扩展块实例。
     * <p>
     * 传入的数据块数组会被克隆（防御性复制），以避免外部后续修改影响当前对象。
     * 构造过程中会进行格式校验，确保数据符合 GIF 规范。
     * </p>
     *
     * @param applicationInformation 应用程序标识符，固定 11 字节，例如 {@code "NETSCAPE2.0"}，
     *                               不能为 {@code null}
     * @param chunkData              应用扩展的数据块内容，不能为 {@code null} 或空数组。
     *                               对于 NETSCAPE2.0 扩展，至少需要 3 字节（1 字节子块 ID + 2 字节循环次数）
     * @throws NullPointerException     如果 {@code applicationInformation} 为 {@code null}
     * @throws IllegalArgumentException 如果 {@code applicationInformation} 长度不为 11，
     *                                  或 {@code chunkData} 为 {@code null}/空数组，
     *                                  或 NETSCAPE2.0 扩展数据长度不足
     */
    public ApplicationExtension(String applicationInformation, byte[] chunkData) {
        this.applicationInformation = applicationInformation;
        this.chunkData = chunkData != null ? chunkData.clone() : null;

        validate();
    }

    /**
     * 内部校验方法，确保扩展块数据符合规范。
     *
     * @throws NullPointerException     如果 {@code applicationInformation} 为 {@code null}
     * @throws IllegalArgumentException 如果标识符长度非 11、数据块为空或 NETSCAPE2.0 数据不完整
     */
    private void validate() {
        Objects.requireNonNull(applicationInformation, "应用程序信息不能为空");

        if (applicationInformation.length() != 11) {
            throw new IllegalArgumentException("应用程序信息必须为 11 字节，实际为：" + applicationInformation.length());
        }

        if (chunkData == null || chunkData.length == 0) {
            throw new IllegalArgumentException("应用程序扩展块数据不能为 null 或空");
        }

        if ("NETSCAPE2.0".equals(applicationInformation) && chunkData.length < 3) {
            throw new IllegalArgumentException("NETSCAPE2.0 扩展块数据长度不能小于 3 字节，实际为：" + chunkData.length);
        }
    }

    // ==================== 访问器 ====================

    /**
     * 返回应用程序标识符字符串（固定 11 字节）。
     *
     * @return 应用程序标识符，例如 {@code "NETSCAPE2.0"}
     */
    public String getApplicationInformation() {
        return applicationInformation;
    }

    /**
     * 返回应用扩展的数据块内容（防御性副本）。
     * <p>
     * 返回的字节数组是内部数据的克隆，修改返回值不会影响当前对象。
     * </p>
     *
     * @return 数据块内容的克隆，可能为 {@code null}（但构造时已校验非空）
     */
    public byte[] getChunkData() {
        return chunkData != null ? chunkData.clone() : null;
    }

    // ==================== 逻辑方法 ====================

    /**
     * 获取 GIF 动画的循环次数（仅适用于 NETSCAPE2.0 扩展）。
     * <p>
     * 解析 NETSCAPE2.0 扩展块的数据，提取循环次数字段。
     * 返回值语义：
     * <ul>
     *   <li>0 — 无限循环</li>
     *   <li>正整数 — 实际循环次数（但 GIF 规范中通常 1 表示不循环，此处将 1 转换为 0 保持统一）</li>
     * </ul>
     * 如果不是 NETSCAPE2.0 扩展或数据不完整，返回 0。
     * </p>
     *
     * @return 循环次数，0 表示无限循环
     */
    public int getLoopCount() {
        int loopCount = 0;  // 0 表示无限循环

        if ("NETSCAPE2.0".equals(applicationInformation) && chunkData.length >= 3 && chunkData[0] == 0x01) {
            loopCount = (chunkData[1] & 0xff) + ((chunkData[2] & 0xff) << 8);
            if (loopCount == 1) {
                loopCount = 0;
            }
        }

        return loopCount;
    }

    // ==================== 访问者模式分派 ====================

    /**
     * 接受一个扩展容器，将当前应用扩展设置到容器中。
     * <p>
     * 这是访问者模式的一部分，用于将扩展块分派到对应的容器槽位。
     * </p>
     *
     * @param extensionContainer 扩展容器，不能为 {@code null}
     */
    @Override
    public void applyTo(ExtensionContainer extensionContainer) {
        extensionContainer.setApplicationExtension(this);
    }
}