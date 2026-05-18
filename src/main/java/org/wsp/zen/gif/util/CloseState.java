package org.wsp.zen.gif.util;

import java.util.Objects;

/**
 * 组件关闭状态管理器，用于统一控制资源或服务的生命周期。
 * <p>
 * 该类提供线程安全的关闭状态检查与标记功能，常用于确保组件在关闭后不再接受新的操作请求。
 * 状态一旦标记为关闭，所有后续的 {@link #checkClosed()} 调用都将抛出
 * {@link IllegalStateException}，且不可逆转。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 内部使用 {@code volatile} 变量 {@code closed} 配合简单读写，保证多线程环境下的可见性。
 * {@link #checkClosed()} 和 {@link #markAsClosed()} 方法均不需要额外同步即可安全调用。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * public class MyComponent {
 *     private final CloseState closeState = new CloseState("MyComponent");
 *
 *     public void doSomething() {
 *         closeState.checkClosed();
 *         // 执行核心逻辑...
 *     }
 *
 *     public void close() {
 *         closeState.markAsClosed();
 *         // 释放资源...
 *     }
 * }
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.gif.core.Decoder
 * @see org.wsp.zen.cache.impl.DefaultCache
 */
public class CloseState {

    /** 组件名称，用于在异常消息中标识是哪个组件已关闭 */
    private final String componentName;

    /** 关闭状态标志，{@code true} 表示已关闭，使用 {@code volatile} 保证可见性 */
    private volatile boolean closed = false;

    /**
     * 构造一个关闭状态管理器。
     *
     * @param componentName 组件名称，用于在抛出异常时提供可读的上下文，不能为 {@code null}
     * @throws NullPointerException 如果 {@code componentName} 为 {@code null}
     */
    public CloseState(String componentName) {
        this.componentName = Objects.requireNonNull(componentName, "组件名称不能为 null");
    }

    /**
     * 检查组件是否已关闭，若已关闭则抛出异常。
     * <p>
     * 该方法应在所有可能受关闭状态影响的公开操作前调用。
     * </p>
     *
     * @throws IllegalStateException 如果组件已被标记为关闭
     */
    public void checkClosed() {
        if (closed) {
            throw new IllegalStateException(componentName + "已关闭，操作失败");
        }
    }

    /**
     * 将组件标记为已关闭。
     * <p>
     * 此操作不可逆，标记后所有后续的 {@link #checkClosed()} 调用都将失败。
     * </p>
     */
    public void markAsClosed() {
        closed = true;
    }

    /**
     * 查询组件是否已关闭。
     *
     * @return {@code true} 如果已关闭，否则 {@code false}
     */
    public boolean isClosed() {
        return closed;
    }
}