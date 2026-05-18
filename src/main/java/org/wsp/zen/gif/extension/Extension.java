package org.wsp.zen.gif.extension;

/**
 * GIF 扩展块（Extension Block）的统一抽象接口。
 * <p>
 * 在 GIF 标准中，扩展块包括图形控制扩展、注释扩展、纯文本扩展和应用扩展等。
 * 此接口采用访问者模式（Visitor Pattern），允许将扩展块的具体处理逻辑委托给
 * {@link ExtensionContainer}，从而实现扩展块类型与操作解耦。
 * </p>
 *
 * <p><b>典型实现：</b>
 * <pre>
 * public class MyCommentExtension implements GifExtension {
 *     private final String comment;
 *
 *     public MyCommentExtension(String comment) {
 *         this.comment = comment;
 *     }
 *
 *     {@literal @}Override
 *     public void accept(GifExtensionContainer container) {
 *         container.handleCommentExtension(this);
 *     }
 *
 *     public String getComment() { return comment; }
 * }
 * </pre>
 *
 * @author wsp
 * @version 1.0
 * @see ExtensionContainer
 */
public interface Extension {

    /**
     * 接受一个扩展块容器，并将自身传递给容器的相应处理方法。
     * <p>
     * 实现类通常在此方法中调用 {@link ExtensionContainer} 的重载方法，
     * 例如 {@code container.handle(this)} 或根据具体扩展类型调用不同的处理接口。
     * 这种方式避免了使用 {@code instanceof} 进行类型判断，符合开闭原则。
     *
     * @param extensionContainer 扩展块容器，用于接收并处理当前扩展块实例，不能为 {@code null}
     * @throws NullPointerException 如果 {@code extensionContainer} 为 {@code null}
     */
    void applyTo(ExtensionContainer extensionContainer);
}