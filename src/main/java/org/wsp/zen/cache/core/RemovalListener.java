package org.wsp.zen.cache.core;

import org.wsp.zen.cache.event.RemovalEvent;

/**
 * 缓存条目移除事件的监听器接口。
 * <p>
 * 当缓存中的条目因淘汰、显式删除、过期或替换等原因被移除时，缓存实现会调用
 * 已注册监听器的 {@link #onRemoval(RemovalEvent)} 方法。通过实现该接口，可以
 * 在条目移除时执行自定义逻辑，例如：
 * <ul>
 *   <li>释放条目值所持有的外部资源（如文件句柄、网络连接、堆外内存）</li>
 *   <li>记录审计日志或监控指标</li>
 *   <li>在其他缓存或持久化存储中同步删除数据</li>
 * </ul>
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * RemovalListener<Integer, BufferedImage> listener = event -> {
 *     BufferedImage image = event.value;
 *     if (image != null) {
 *         image.flush(); // 释放图像资源
 *     }
 *     System.out.println("移除帧: " + event.key);
 * };
 * cache.addRemovalListener(listener);
 * }</pre>
 *
 * <p><b>执行语义：</b>
 * 监听器的执行线程取决于具体的 {@link Cache} 实现：
 * <ul>
 *   <li><b>同步执行</b>：在移除操作的调用线程中直接执行，实现简单但需注意避免阻塞缓存正常操作。</li>
 *   <li><b>异步执行</b>：在独立的回调线程池中执行，避免阻塞但需考虑线程安全。</li>
 * </ul>
 * 实现者应根据缓存实现文档了解具体语义，并确保 {@code onRemoval} 方法执行迅速且不抛出未捕获异常。
 * </p>
 *
 * <p><b>移除原因：</b>
 * 缓存实现可通过移除事件携带具体的移除原因（如 {@link RemovalEvent} 的扩展类），
 * 但当前提供的 {@link RemovalEvent} 仅包含键和值，不直接描述移除原因。
 * 若需区分不同移除场景，可结合上下文或使用更丰富的缓存实现。
 * </p>
 *
 * @param <K> 缓存键的类型
 * @param <V> 缓存值的类型
 * @author wsp
 * @version 1.0
 * @see RemovalEvent
 * @see Cache#addRemovalListener(RemovalListener)
 */
public interface RemovalListener<K, V> {

    /**
     * 当缓存中的条目被移除时调用。
     * <p>
     * 实现此方法时应避免执行长时间运行的操作，以免影响缓存的整体性能（尤其当监听器为同步调用时）。
     * 抛出未捕获异常可能导致缓存操作的正常流程中断，故推荐在方法内自行处理异常。
     * </p>
     *
     * @param event 包含被移除条目的键和值的事件对象，不能为 {@code null}
     */
    void onRemoval(RemovalEvent<K, V> event);
}