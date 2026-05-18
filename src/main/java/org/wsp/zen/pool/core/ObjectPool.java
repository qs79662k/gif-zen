package org.wsp.zen.pool.core;

/**
 * 对象池接口，用于复用特定类型的对象，减少频繁创建和销毁带来的开销。
 * <p>
 * 池中的对象通过 {@link #obtain()} 获取，使用完毕后通过 {@link #release(Object)} 归还。
 * 归还时工厂会调用 {@link ObjectFactory#reset(Object)} 准备下一次使用。
 * </p>
 *
 * <h3>生命周期管理</h3>
 * <ul>
 *   <li>{@link #obtain()} / {@link #release(Object)}：正常借用归还。</li>
 *   <li>{@link #clear()}：清空池中所有空闲对象并销毁，但<b>保留工厂已学习到的尺寸</b>。</li>
 *   <li>{@link #reset()}：清空池中所有空闲对象并销毁，<b>同时重置工厂状态至未学习</b>，后续借用将重新开始学习尺寸。</li>
 *   <li>{@link #close()}：永久关闭池，释放所有资源，关闭后不可再用。</li>
 * </ul>
 *
 * @param <T> 池中对象的类型，如 {@link java.awt.image.BufferedImage}、{@code byte[]} 等
 * @author wsp
 * @version 1.0
 * @see ObjectFactory
 */
public interface ObjectPool<T> extends AutoCloseable {

    /**
     * 从池中借出一个可用对象。
     * <p>
     * 如果池中有空闲对象，直接返回；否则调用 {@link ObjectFactory#create()} 创建一个新对象。
     * 若工厂尚未学习尺寸且无法创建，允许返回 {@code null}（取决于具体实现约定）。
     * </p>
     *
     * @return 可用的 T 类型对象，可能为 {@code null}（工厂未初始化时）
     * @throws IllegalStateException 如果池已关闭
     */
    T obtain();

    /**
     * 将对象归还到池中，以便后续复用。
     * <p>
     * 归还前会调用 {@link ObjectFactory#reset(Object)} 重置对象状态（如学习尺寸）。
     * 如果池未满，对象被放入空闲队列；否则直接销毁。
     * 调用者归还后不应再使用该对象。
     * </p>
     *
     * @param obj 先前通过 {@link #obtain()} 或其他方式创建的对象，不能为 {@code null}
     * @throws NullPointerException 如果 {@code object} 为 {@code null}
     * @throws IllegalStateException 如果池已关闭
     */
    void release(T obj);

    /**
     * 清空池中所有空闲对象并调用工厂的 {@link ObjectFactory#destroy(Object)} 销毁。
     * <p>
     * 此操作<b>不会重置工厂已学习的尺寸</b>，后续 {@link #obtain()} 依然按之前学习到的最大尺寸创建对象。
     * 如果希望彻底丢弃尺寸信息并从头学习，请使用 {@link #reset()}。
     * </p>
     * <p>
     * 通常在需要释放一部分内存但保持当前尺寸学习成果时使用。
     * </p>
     */
    void clear();

    /**
     * 重置整个对象池的学习状态。
     * <p>
     * 依次执行：
     * <ol>
     *   <li>移出所有空闲对象并调用工厂的 {@link ObjectFactory#destroy(Object)} 销毁。</li>
     *   <li>调用工厂的 {@link ObjectFactory#clear()} 重置其内部学习状态（如最大尺寸清零）。</li>
     * </ol>
     * 重置后池仍然可用，下次 {@link #obtain()} 将返回 {@code null}（工厂未初始化），
     * 等待调用者归还第一个对象以重新学习尺寸。
     * </p>
     * <p>
     * 典型场景：切换工作负载时（如打开新文件），旧的最大尺寸不再适用，避免内存浪费。
     * </p>
     *
     * @throws IllegalStateException 如果池已关闭
     */
    void reset();

    /**
     * 永久关闭对象池，释放所有资源。
     * <p>
     * 关闭后，池中所有空闲对象都会被销毁（调用 {@link ObjectFactory#destroy(Object)}），
     * 后续任何对 {@link #obtain()}、{@link #release(Object)}、{@link #clear()} 或 {@link #reset()} 的调用
     * 都应抛出 {@link IllegalStateException}。
     * </p>
     */
    @Override
    void close();

    /**
     * 获取当前池中可用的空闲对象数量（仅用于监控和调试）。
     *
     * @return 空闲对象数量
     */
    default int getAvailableCount() {
        return 0;
    }
}