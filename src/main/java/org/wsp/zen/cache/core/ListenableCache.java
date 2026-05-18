package org.wsp.zen.cache.core;

/**
 * 支持移除事件监听的键值缓存接口，继承自 {@link BasicCache}。
 * <p>
 * 通过注册 {@link RemovalListener}，可以在缓存条目被移除（如手动删除、驱逐或过期）时收到通知，
 * 从而执行自定义的资源清理、日志记录或数据同步等操作。
 * </p>
 *
 * @param <K> 缓存键的类型
 * @param <V> 缓存值的类型
 * @author wsp
 * @version 1.0
 */
public interface ListenableCache<K, V> extends BasicCache<K, V> {

    /**
     * 注册一个移除事件监听器。
     * <p>
     * 当缓存条目因任何原因被移除时，会调用已注册监听器的对应回调方法。
     * 同一监听器可以被添加到多个缓存实例。
     * </p>
     *
     * @param listener 要注册的移除监听器，不能为 {@code null}
     * @throws NullPointerException 如果 {@code listener} 为 {@code null}
     */
    void addRemovalListener(RemovalListener<K, V> listener);

    /**
     * 移除先前注册的移除事件监听器。
     * <p>
     * 如果该监听器已被多次注册，此方法将移除最先匹配的一个实例（具体行为取决于实现）。
     * 若监听器不存在，则调用无效果。
     * </p>
     *
     * @param listener 要移除的监听器，不能为 {@code null}
     * @throws NullPointerException 如果 {@code listener} 为 {@code null}
     */
    void removeRemovalListener(RemovalListener<K, V> listener);
}