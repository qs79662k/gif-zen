package org.wsp.zen.gif.extension;

/**
 * GIF 注释扩展块（Comment Extension）的实现类。
 * <p>
 * 注释扩展块用于在 GIF 文件中嵌入纯文本注释信息，通常由编码器添加以描述图像内容、
 * 版权信息或其他元数据。一个 GIF 文件中可以包含多个注释扩展块。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * byte[] commentBytes = "Created by GIPHY".getBytes(StandardCharsets.US_ASCII);
 * CommentExtension comment = new CommentExtension(commentBytes);
 * String text = new String(comment.getCommentData(), StandardCharsets.US_ASCII);
 * System.out.println("GIF 注释: " + text);
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see Extension
 * @see org.wsp.zen.gif.extension.ExtensionContainer
 */
public class CommentExtension implements Extension {

    /**
     * 注释数据块的内容（已克隆，防御性复制）。
     */
    private final byte[] commentData;

    // ==================== 构造器 ====================

    /**
     * 构造一个注释扩展块实例。
     * <p>
     * 传入的字节数组会被克隆（防御性复制），以避免外部后续修改影响当前对象。
     * 注释数据通常为 ASCII 编码的文本，但 GIF 规范并未强制规定字符集。
     * </p>
     *
     * @param commentData 注释数据块的内容，可以为 {@code null}（将存储为 {@code null}）
     */
    public CommentExtension(byte[] commentData) {
        this.commentData = commentData != null ? commentData.clone() : null;
    }

    // ==================== 访问器 ====================

    /**
     * 返回注释数据块的内容（防御性副本）。
     * <p>
     * 返回的字节数组是内部数据的克隆，修改返回值不会影响当前对象。
     * </p>
     *
     * @return 注释数据的克隆，可能为 {@code null}
     */
    public byte[] getCommentData() {
        return commentData != null ? commentData.clone() : null;
    }

    // ==================== 访问者模式分派 ====================

    /**
     * 接受一个扩展容器，将当前注释扩展添加到容器中。
     * <p>
     * 这是访问者模式的一部分，用于将扩展块分派到对应的容器槽位。
     * </p>
     *
     * @param extensionContainer 扩展容器，不能为 {@code null}
     */
    @Override
    public void applyTo(ExtensionContainer extensionContainer) {
        extensionContainer.addCommentExtension(this);
    }
}