package org.wsp.zen.cache.impl;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.wsp.zen.cache.core.Cache;
import org.wsp.zen.cache.core.CacheManager;
import org.wsp.zen.gif.util.CloseState;

/**
 * {@link CacheManager} 的线程安全默认实现。
 * <p>
 * 内部使用 {@link ConcurrentHashMap} 存储缓存实例，按名称进行索引。
 * 配合 {@link CloseState} 保证管理器关闭后所有操作都会被安全拒绝。
 * </p>
 *
 * <h3>重要行为变更</h3>
 * <ul>
 *   <li>{@link #removeCache(String)} 和 {@link #removeAll()} 现在会<b>自动关闭</b>被移除的缓存，
 *       以避免资源泄漏。关闭时抛出的 {@link IOException} 会被包装为 {@link RuntimeException} 抛出。</li>
 *   <li>如需分离引用但不关闭，请使用 {@link #closeCache(String)} 或 {@link #closeAllCaches()}，
 *       它们同样会移除并关闭缓存。</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <ul>
 *   <li>单个操作（如 {@link #register}、{@link #getCache}、{@link #clearCache}）
 *       通过 {@link ConcurrentHashMap} 的原子方法保证线程安全。</li>
 *   <li>批量操作（如 {@link #clearAllCaches()}、{@link #removeAll()})
 *       在迭代过程中不会抛出 {@link java.util.ConcurrentModificationException}，
 *       但可能看到弱一致性的结果：并发的注册或移除可能不会反映在当前遍历中，这在管理场景下是可接受的。</li>
 *   <li>{@link #removeAll()} 使用 {@link Iterator#remove()} 安全移除每个条目，
 *       并收集可能抛出的首个异常（包装为 {@link RuntimeException}），确保所有缓存都被尝试关闭。</li>
 *   <li>{@link #close()} 方法先标记管理器为已关闭，再执行批量关闭，
 *       避免标记后仍有新缓存注册导致未关闭的泄漏。</li>
 * </ul>
 *
 * <h3>类型安全</h3>
 * 本实现<b>不存储</b>键值类型信息。{@link #getCache(String)} 返回的缓存类型
 * 仅由调用者自行保证一致。若注册时是 {@code Cache<Integer, User>}，
 * 但调用时误写成 {@code Cache<String, User>}，编译期不会报错，但运行时可能产生
 * {@link ClassCastException}。
 *
 * @author wsp
 * @version 1.0
 * @see Cache
 * @see CacheManager
 */
public class DefaultCacheManager implements CacheManager {

    /**
     * 缓存注册表，键为名称，值为缓存实例。
     */
    private final Map<String, Cache<?, ?>> registry = new ConcurrentHashMap<>();

    /**
     * 生命周期状态管理。
     */
    private final CloseState closeState = new CloseState("缓存管理器");

    // ==================== 注册与查询 ====================

    /**
     * 注册一个缓存实例。
     * <p>
     * 若同名缓存已存在，则忽略本次注册并返回 {@code false}，
     * 原有缓存不会被替换或关闭。如需替换，请先调用 {@link #removeCache(String)} 或 {@link #closeCache(String)}。
     * </p>
     *
     * @param name  缓存名称，不能为 {@code null}
     * @param cache 缓存实例，不能为 {@code null}
     * @return 首次注册返回 {@code true}；若名称已存在返回 {@code false}
     * @throws NullPointerException  如果任一参数为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public boolean register(String name, Cache<?, ?> cache) {
        closeState.checkClosed();
        Objects.requireNonNull(name, "缓存名称不能为 null");
        Objects.requireNonNull(cache, "缓存实例不能为 null");
        return registry.putIfAbsent(name, cache) == null;
    }

    /**
     * 根据名称获取缓存实例。
     * <p>
     * 返回的缓存被强制转换为调用者期望的泛型类型，
     * 类型安全性由调用者保证。若实际类型不匹配，后续缓存操作可能抛出
     * {@link ClassCastException}。
     * </p>
     *
     * @param name 缓存名称，不能为 {@code null}
     * @param <K>  期望的键类型
     * @param <V>  期望的值类型
     * @return 对应的缓存实例，若名称不存在则返回 {@code null}
     * @throws NullPointerException  如果 {@code name} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Cache<K, V> getCache(String name) {
        closeState.checkClosed();
        Objects.requireNonNull(name, "缓存名称不能为 null");
        return (Cache<K, V>) registry.get(name);
    }

    /**
     * 检查指定名称的缓存是否已注册。
     *
     * @param name 缓存名称，不能为 {@code null}
     * @return 若已注册返回 {@code true}
     * @throws NullPointerException  如果 {@code name} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public boolean contains(String name) {
        closeState.checkClosed();
        Objects.requireNonNull(name, "缓存名称不能为 null");
        return registry.containsKey(name);
    }

    /**
     * 返回当前注册的缓存数量。
     *
     * @return 缓存数量
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public int getCacheCount() {
        closeState.checkClosed();
        return registry.size();
    }

    /**
     * 返回所有已注册缓存名称的不可变快照。
     *
     * @return 包含所有名称的 {@link Set}
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public Set<String> getAllCacheNames() {
        closeState.checkClosed();
        return Set.copyOf(registry.keySet());
    }

    // ==================== 移除与关闭（单例） ====================

    /**
     * 移除并返回指定名称的缓存实例，<b>同时关闭该缓存</b>。
     * <p>
     * 关闭时若抛出 {@link IOException}，会将其包装为 {@link RuntimeException} 抛出，
     * 以确保调用方能感知资源释放失败。关闭后返回的缓存实例不应再被使用，否则可能抛出异常。
     * </p>
     * <p>
     * <b>注意：</b>不关闭直接移除会导致资源（如连接、文件句柄等）泄漏，因此本方法强制关闭。
     * 如果确实需要“只移除不关闭”，请考虑转移所有权或使用其他方式。
     * </p>
     *
     * @param name 缓存名称，不能为 {@code null}
     * @return 被移除的缓存实例（已关闭），若不存在则返回 {@code null}
     * @throws NullPointerException  如果 {@code name} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭，或关闭缓存时发生 I/O 错误（包装后）
     */
    @Override
    public Cache<?, ?> removeCache(String name) {
        closeState.checkClosed();
        Objects.requireNonNull(name, "缓存名称不能为 null");

        Cache<?, ?> cache = registry.remove(name);
        if (cache != null) {
            try {
                cache.close();
            } catch (IOException e) {
                throw new RuntimeException("关闭缓存[" + name + "]失败", e);
            }
        }
        return cache;
    }

    /**
     * 关闭并移除指定名称的缓存。
     * <p>
     * 若缓存存在，先调用其 {@link Cache#close()} 释放资源，
     * 然后从管理器中移除。若名称不存在，方法静默返回。
     * </p>
     *
     * @param name 缓存名称，不能为 {@code null}
     * @throws IOException          如果缓存关闭过程中发生 I/O 错误
     * @throws NullPointerException 如果 {@code name} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public void closeCache(String name) throws IOException {
        closeState.checkClosed();
        Objects.requireNonNull(name, "缓存名称不能为 null");

        Cache<?, ?> cache = registry.remove(name);
        if (cache != null) {
            cache.close();
        }
    }

    // ==================== 移除与关闭（批量） ====================

    /**
     * 移除所有已注册的缓存，<b>同时关闭它们</b>。
     * <p>
     * 遍历所有缓存，逐个从注册表中移除并调用 {@link Cache#close()}。
     * 若某个缓存关闭时抛出 {@link IOException}，会收集第一个异常，
     * 在所有缓存处理完成后将其包装为 {@link RuntimeException} 抛出。
     * 确保尽最大努力关闭每个缓存。
     * </p>
     * <p>
     * <b>注意：</b>不关闭直接清空会导致大量资源泄漏，因此本方法强制关闭所有缓存。
     * </p>
     *
     * @throws IllegalStateException 如果管理器已关闭，或至少一个缓存关闭失败（包装后）
     */
    @Override
    public void removeAll() {
        closeState.checkClosed();

        IOException firstException = null;
        Iterator<Map.Entry<String, Cache<?, ?>>> it = registry.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Cache<?, ?>> entry = it.next();
            // 先移除，避免重复关闭
            it.remove();
            try {
                entry.getValue().close();
            } catch (IOException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }

        // 若有关闭失败，抛出包装异常
        if (firstException != null) {
            throw new RuntimeException("关闭一个或多个缓存失败", firstException);
        }
    }

    /**
     * 关闭并移除所有已注册的缓存。
     * <p>
     * 使用迭代器安全遍历并逐个移除缓存，调用其 {@link Cache#close()} 方法。
     * 若任一缓存关闭时抛出 {@link IOException}，会收集首个异常并在
     * 其他缓存关闭完成后抛出，确保尽最大努力关闭所有缓存。
     * </p>
     *
     * @throws IOException          如果至少一个缓存关闭失败
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public void closeAllCaches() throws IOException {
        closeState.checkClosed();

        IOException firstException = null;
        Iterator<Map.Entry<String, Cache<?, ?>>> it = registry.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Cache<?, ?>> entry = it.next();
            it.remove();
            try {
                entry.getValue().close();
            } catch (IOException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }

        if (firstException != null) {
            throw firstException;
        }
    }

    // ==================== 清空操作 ====================

    /**
     * 清空指定缓存中的所有条目。
     * <p>
     * 该操作委托给对应缓存的 {@link Cache#clear()} 方法，
     * 缓存实例及其配置不受影响，也不会关闭或移除缓存。
     * </p>
     *
     * @param name 缓存名称，不能为 {@code null}
     * @throws NullPointerException  如果 {@code name} 为 {@code null}
     * @throws IllegalArgumentException 如果指定名称的缓存不存在
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public void clearCache(String name) {
        closeState.checkClosed();
        Objects.requireNonNull(name, "缓存名称不能为 null");

        Cache<?, ?> cache = registry.get(name);
        if (cache == null) {
            throw new IllegalArgumentException("缓存[" + name + "]不存在");
        }
        cache.clear();
    }

    /**
     * 清空所有已注册缓存中的条目。
     * <p>
     * 遍历当前所有缓存并调用其 {@link Cache#clear()} 方法。
     * 遍历使用弱一致性视图，并发添加的缓存可能不被清理，但一般场景下可接受。
     * </p>
     *
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public void clearAllCaches() {
        closeState.checkClosed();
        for (Cache<?, ?> cache : registry.values()) {
            cache.clear();
        }
    }

    // ==================== 生命周期管理 ====================

    /**
     * 关闭整个管理器，释放所有资源。
     * <p>
     * 方法会首先检查是否已关闭，避免重复操作。
     * 若未关闭，则标记为已关闭状态，然后调用 {@link #closeAllCaches()} 关闭所有注册的缓存。
     * 标记操作保证后续任何新注册都会被拒绝，从而防止关闭过程中新缓存加入导致泄漏。
     * </p>
     *
     * @throws IOException 如果关闭任一缓存时发生 I/O 错误
     */
    @Override
    public void close() throws IOException {
        // 避免重复关闭
        if (closeState.isClosed()) {
            return;
        }

        // 先标记为已关闭，阻止新注册
        closeState.markAsClosed();
        // 再关闭所有缓存
        closeAllCaches();
    }
}