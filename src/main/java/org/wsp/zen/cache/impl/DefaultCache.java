package org.wsp.zen.cache.impl;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.wsp.zen.cache.core.EvictionPolicy;
import org.wsp.zen.cache.core.Cache;
import org.wsp.zen.cache.core.RemovalListener;
import org.wsp.zen.cache.event.RemovalEvent;
import org.wsp.zen.gif.util.CloseState;

/**
 * 基于 {@link ConcurrentHashMap} 的默认键值缓存实现。
 * <p>
 * 该实现提供了线程安全的缓存操作，支持：
 * <ul>
 *   <li>通过 {@link #fetchOrCompute} 实现原子性的“获取或加载”语义</li>
 *   <li>基于驱逐策略的批量条目移除（{@link #evictEntries}）</li>
 *   <li>同步通知的移除监听器（{@link RemovalListener}），适合轻量级回调（如对象归还、资源释放）</li>
 *   <li>受 {@link CloseState} 管理的生命周期，关闭后所有操作均抛出异常</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 内部使用 {@link ConcurrentHashMap} 保证读写操作的并发安全；监听器列表使用 {@link CopyOnWriteArrayList}
 * 支持在遍历时安全地增删监听器。移除通知在调用线程中同步执行，监听器应避免耗时操作。
 * </p>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * DefaultCache<String, User> cache = new DefaultCache<>();
 * cache.addRemovalListener(event -> {
 *     // 处理被移除的用户对象，例如归还连接池
 * });
 * User user = cache.fetchOrCompute("user1", key -> userService.load(key));
 * // ... 业务操作
 * cache.close(); // 关闭并清空
 * }</pre>
 * </p>
 *
 * @param <K> 缓存键的类型
 * @param <V> 缓存值的类型
 * @author wsp
 * @version 1.0
 * @see Cache
 * @see RemovalListener
 * @see EvictionPolicy
 * @see CloseState
 */
public class DefaultCache<K, V> implements Cache<K, V> {

    /**
     * 存储键值对的并发映射，所有读写操作都通过此 Map 进行。
     */
    private final Map<K, V> store = new ConcurrentHashMap<>();

    /**
     * 移除监听器列表，使用 CopyOnWriteArrayList 保证遍历时的线程安全性。
     */
    private final List<RemovalListener<K, V>> listeners = new CopyOnWriteArrayList<>();

    /**
     * 缓存生命周期状态管理，用于防止关闭后的非法操作。
     */
    private final CloseState closeState = new CloseState("缓存");

    // ==================== 查询操作 ====================

    /**
     * 检查指定键是否已缓存。
     *
     * @param key 缓存键，不能为 {@code null}
     * @return {@code true} 如果键存在于缓存中，否则 {@code false}
     * @throws NullPointerException 如果 {@code key} 为 {@code null}
     * @throws IllegalStateException 如果缓存已关闭
     */
    @Override
    public boolean isCached(K key) {
        closeState.checkClosed();
        Objects.requireNonNull(key, "缓存键不能为 null");
        return store.containsKey(key);
    }

    /**
     * 返回缓存中的条目数量。
     *
     * @return 当前缓存的键值对数量
     * @throws IllegalStateException 如果缓存已关闭
     */
    @Override
    public int size() {
        closeState.checkClosed();
        return store.size();
    }

    /**
     * 获取指定键对应的值，如果不存在则返回 {@code null}。
     *
     * @param key 缓存键，不能为 {@code null}
     * @return 缓存的值，可能为 {@code null}
     * @throws NullPointerException 如果 {@code key} 为 {@code null}
     * @throws IllegalStateException 如果缓存已关闭
     */
    @Override
    public V get(K key) {
        closeState.checkClosed();
        Objects.requireNonNull(key, "缓存键不能为 null");
        return store.get(key);
    }

    // ==================== 原子加载操作 ====================

    /**
     * 获取指定键对应的值，如果不存在则使用提供的加载函数计算并原子性地存入缓存。
     * <p>
     * 此方法保证对同一键的并发调用只会执行一次加载操作，且加载期间其他线程会等待结果。
     * 如果加载函数返回 {@code null}，则不会存储任何值，且后续调用将再次尝试加载。
     * </p>
     * <p>
     * 实现细节：使用 {@link ConcurrentHashMap#compute} 确保原子性。
     * </p>
     *
     * @param key    缓存键，不能为 {@code null}
     * @param loader 加载函数，输入为键，输出为要缓存的值，不能为 {@code null}
     * @return 缓存中已有的值或新加载的值，可能为 {@code null}（当加载器返回 {@code null} 时）
     * @throws NullPointerException 如果 {@code key} 或 {@code loader} 为 {@code null}
     * @throws IllegalStateException 如果缓存已关闭
     */
    @Override
    public V fetchOrCompute(K key, Function<K, V> loader) {
        closeState.checkClosed();
        Objects.requireNonNull(key, "缓存键不能为 null");
        Objects.requireNonNull(loader, "加载函数不能为 null");

        // 快速路径：检查是否已存在非空值
        V cachedValue = store.get(key);
        if (cachedValue != null) {
            return cachedValue;
        }

        // 原子计算：仅当键不存在或值为 null 时才执行加载
        return store.compute(key, (k, existingValue) -> {
            if (existingValue == null) {
                V value = loader.apply(k);
                // 仅当加载结果非 null 时才存入
                if (value != null) {
                    return value;
                }
            }
            return existingValue;
        });
    }

    // ==================== 写操作 ====================

    /**
     * 将指定的键值对存入缓存。
     * <p>
     * 如果键之前已存在对应的值，旧值将被新值覆盖，并且会触发 {@link RemovalListener}
     * 通知（传入旧值）。这保证了与对象池等资源管理机制的一致性，避免资源泄漏。
     * </p>
     *
     * @param key   缓存键，不能为 {@code null}
     * @param value 缓存值，不能为 {@code null}
     * @throws NullPointerException 如果 {@code key} 或 {@code value} 为 {@code null}
     * @throws IllegalStateException 如果缓存已关闭
     */
    @Override
    public void put(K key, V value) {
        closeState.checkClosed();
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        V old = store.put(key, value);
        // 若存在旧值，通知监听器
        if (old != null) {
            notifyRemoval(key, old);
        }
    }

    /**
     * 移除指定键对应的条目，并返回被移除的值。
     * <p>
     * 移除操作会同步通知所有已注册的 {@link RemovalListener}。
     * </p>
     *
     * @param key 缓存键，不能为 {@code null}
     * @return 被移除的值，如果键不存在则返回 {@code null}
     * @throws NullPointerException 如果 {@code key} 为 {@code null}
     * @throws IllegalStateException 如果缓存已关闭
     */
    @Override
    public V remove(K key) {
        closeState.checkClosed();
        Objects.requireNonNull(key, "缓存键不能为 null");

        V value = store.remove(key);
        if (value != null) {
            notifyRemoval(key, value);
        }
        return value;
    }

    // ==================== 批量移除与清空 ====================

    /**
     * 根据驱逐策略批量移除符合条件的缓存条目。
     * <p>
     * 实现步骤：
     * <ol>
     *   <li>遍历当前所有键，收集 {@link EvictionPolicy#isEvictable(Object)} 返回 {@code true} 的键</li>
     *   <li>遍历收集到的键，从缓存中移除并通知监听器</li>
     * </ol>
     * 该过程不保证原子性，但避免了在遍历过程中因移除导致的并发修改异常。
     * </p>
     *
     * @param evictionPolicy 驱逐策略，决定哪些键应被移除，不能为 {@code null}
     * @throws NullPointerException 如果 {@code evictionPolicy} 为 {@code null}
     * @throws IllegalStateException 如果缓存已关闭
     */
    @Override
    public void evictEntries(EvictionPolicy<K> evictionPolicy) {
        closeState.checkClosed();
        Objects.requireNonNull(evictionPolicy, "驱逐策略不能为 null");

        // 步骤1：收集需要驱逐的键（避免在遍历 Map 时直接修改）
        Set<K> keysToEvict = new HashSet<>();
        for (K key : store.keySet()) {
            if (evictionPolicy.isEvictable(key)) {
                keysToEvict.add(key);
            }
        }

        // 步骤2：移除并通知监听器
        for (K key : keysToEvict) {
            V value = store.remove(key);
            if (value != null) {
                notifyRemoval(key, value);
            }
        }
    }

    /**
     * 清空缓存中的所有条目。
     * <p>
     * 每个被移除的条目都会触发 {@link RemovalListener} 通知。
     * </p>
     *
     * @throws IllegalStateException 如果缓存已关闭
     */
    @Override
    public void clear() {
        closeState.checkClosed();

        // 获取键快照，避免并发修改
        Set<K> cacheKeySnapshot = new HashSet<>(store.keySet());
        for (K key : cacheKeySnapshot) {
            V value = store.remove(key);
            if (value != null) {
                notifyRemoval(key, value);
            }
        }
    }

    // ==================== 监听器管理 ====================

    /**
     * 注册一个移除监听器，当缓存条目被移除时将会收到通知。
     * <p>
     * 监听器以同步方式在被移除的线程中调用，因此应避免执行耗时操作。
     * 监听器列表使用 {@link CopyOnWriteArrayList}，支持在迭代过程中安全地添加或删除监听器。
     * </p>
     *
     * @param listener 要添加的监听器，不能为 {@code null}
     * @throws NullPointerException 如果 {@code listener} 为 {@code null}
     * @throws IllegalStateException 如果缓存已关闭
     */
    @Override
    public void addRemovalListener(RemovalListener<K, V> listener) {
        closeState.checkClosed();
        Objects.requireNonNull(listener, "监听器不能为 null");
        listeners.add(listener);
    }

    /**
     * 移除之前注册的移除监听器。
     *
     * @param listener 要移除的监听器
     * @throws IllegalStateException 如果缓存已关闭
     */
    @Override
    public void removeRemovalListener(RemovalListener<K, V> listener) {
        closeState.checkClosed();
        listeners.remove(listener);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 同步通知所有已注册的移除监听器。
     * <p>
     * 如果监听器抛出 {@link NoSuchElementException}（通常表示监听器列表在遍历过程中被修改），
     * 会捕获并输出信息后继续处理其他监听器；其他异常会被捕获并打印错误栈。
     * </p>
     *
     * @param key   被移除的键
     * @param value 被移除的值
     */
    private void notifyRemoval(K key, V value) {
        if (key == null || value == null) {
            return;
        }

        RemovalEvent<K, V> event = new RemovalEvent<>(key, value);

        for (RemovalListener<K, V> listener : listeners) {
            try {
                listener.onRemoval(event);
            } catch (NoSuchElementException e) {
                // 监听器列表在遍历过程中发生变化（例如被并发移除），忽略此异常
                System.out.println("移除通知完成：监听器列表在遍历过程中发生变化");
            } catch (Exception e) {
                // 其他异常应予以记录，但不中断对其他监听器的通知
                System.err.println("缓存移除监听器处理失败，key：" + key + "。" + e);
            }
        }
    }

    // ==================== 生命周期管理 ====================

    /**
     * 关闭缓存，释放所有资源。
     * <p>
     * 关闭操作会先清空所有缓存条目（触发移除通知），然后清空监听器列表，并将缓存标记为已关闭。
     * 关闭后任何尝试访问缓存的操作都会抛出 {@link IllegalStateException}。
     * </p>
     *
     * @throws IOException 从不抛出，但为了满足 {@link AutoCloseable} 签名而声明
     */
    @Override
    public void close() throws IOException {
        closeState.checkClosed();

        // 清空缓存条目，触发所有移除通知
        clear();
        // 移除所有监听器
        listeners.clear();
        // 标记为已关闭
        closeState.markAsClosed();
    }
}