package org.wsp.zen.gif.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * GIF 扩展块容器，用于收集和存储解析过程中遇到的各种扩展块。
 * <p>
 * 该类作为 GIF 解析过程中扩展块数据的临时汇聚点，支持存储：
 * <ul>
 *   <li>应用扩展块（{@link ApplicationExtension}）</li>
 *   <li>注释扩展块列表（{@link CommentExtension}）</li>
 *   <li>纯文本扩展块列表（{@link PlainTextExtension}）</li>
 *   <li>图形控制扩展块（{@link GraphicsControlExtension}）</li>
 * </ul>
 * 其中图形控制扩展块是临时的，通常每个图像帧会关联一个，解析下一帧时可能被覆盖。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该类未做同步控制，不适合在多线程环境下并发修改。通常由解析器在单线程内顺序使用。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * ExtensionContainer container = new ExtensionContainer();
 * container.setApplicationExtension(appExt);
 * container.addCommentExtension(commentExt);
 * container.setGraphicsControlExtension(graphicsControl);
 *
 * // 读取扩展信息
 * ApplicationExtension app = container.getApplicationExtension();
 * List<CommentExtension> comments = container.getCommentExtensions();
 * GraphicsControlExtension gce = container.getGraphicsControlExtension();
 *
 * // 清理以备下一帧使用
 * container.clear();
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see ApplicationExtension
 * @see CommentExtension
 * @see PlainTextExtension
 * @see GraphicsControlExtension
 */
public class ExtensionContainer {

    /**
     * 应用程序扩展块（如 NETSCAPE2.0 循环次数定义）。
     */
    private ApplicationExtension application;

    /**
     * 注释扩展块列表，可能包含多个注释。
     */
    private final List<CommentExtension> comments = new ArrayList<>();

    /**
     * 纯文本扩展块列表，用于嵌入可渲染文本。
     */
    private final List<PlainTextExtension> plainTexts = new ArrayList<>();

    /**
     * 图形控制扩展块，与当前帧关联，临时性数据。
     */
    private GraphicsControlExtension graphicsControl;

    // ==================== 设置器 ====================

    /**
     * 设置应用程序扩展块。
     * <p>
     * 一个 GIF 通常最多只有一个应用扩展块，重复设置会覆盖之前的值。
     * </p>
     *
     * @param application 应用程序扩展块对象，不能为 {@code null}
     * @throws NullPointerException 如果 {@code application} 为 {@code null}
     */
    public void setApplicationExtension(ApplicationExtension application) {
        this.application = Objects.requireNonNull(application, "应用程序扩展块对象不能为 null");
    }

    /**
     * 添加一个注释扩展块。
     * <p>
     * GIF 允许存在多个注释扩展块，调用此方法将追加到列表末尾。
     * </p>
     *
     * @param comment 注释扩展块对象，可以为 {@code null}（但建议传入有效对象）
     */
    public void addCommentExtension(CommentExtension comment) {
        comments.add(comment);
    }

    /**
     * 添加一个纯文本扩展块。
     *
     * @param plainText 纯文本扩展块对象，可以为 {@code null}
     */
    public void addPlainTextExtension(PlainTextExtension plainText) {
        plainTexts.add(plainText);
    }

    /**
     * 设置图形控制扩展块（临时性）。
     * <p>
     * 图形控制扩展通常与紧随其后的图像帧相关联，解析新帧前应清空或覆盖。
     * </p>
     *
     * @param graphicsControl 图形控制扩展块对象，可以为 {@code null}
     */
    public void setGraphicsControlExtension(GraphicsControlExtension graphicsControl) {
        this.graphicsControl = graphicsControl;
    }

    // ==================== 访问器 ====================

    /**
     * 获取当前存储的应用程序扩展块。
     *
     * @return 应用程序扩展块实例，如果未设置则返回 {@code null}
     */
    public ApplicationExtension getApplicationExtension() {
        return application;
    }

    /**
     * 获取所有注释扩展块的不可修改列表。
     *
     * @return 包含所有注释扩展块的只读列表，永远不为 {@code null}
     */
    public List<CommentExtension> getCommentExtensions() {
        return Collections.unmodifiableList(comments);
    }

    /**
     * 获取所有纯文本扩展块的不可修改列表。
     *
     * @return 包含所有纯文本扩展块的只读列表，永远不为 {@code null}
     */
    public List<PlainTextExtension> getPlainTextExtensions() {
        return Collections.unmodifiableList(plainTexts);
    }

    /**
     * 获取当前关联的图形控制扩展块。
     *
     * @return 图形控制扩展块实例，如果未设置则返回 {@code null}
     */
    public GraphicsControlExtension getGraphicsControlExtension() {
        return graphicsControl;
    }

    // ==================== 清理 ====================

    /**
     * 清空容器中的所有扩展块数据。
     * <p>
     * 通常在解析完一帧后调用，为下一帧的扩展块收集做准备。
     * </p>
     */
    public void clear() {
        application = null;
        comments.clear();
        plainTexts.clear();
        graphicsControl = null;
    }
}