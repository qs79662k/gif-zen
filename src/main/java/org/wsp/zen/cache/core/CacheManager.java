package org.wsp.zen.cache.core;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

/**
 * 缓存管理器，按名称统一管理多个 {@link Cache} 实例。
 * <p>
 * 管理器只负责缓存实例的注册、查找、移除、清空、关闭及状态查询，
 * <b>不介入缓存内部数据的存取</b>。获取到 {@link Cache} 实例后，
 * 由调用者自行进行数据的 {@code get} / {@code put} 等操作。
 * </p>
 *
 * <h3>重要行为说明</h3>
 * <ul>
 *   <li>{@link #removeCache(String)} 和 {@link #removeAll()} 在实现中会<b>自动关闭</b>被移除的缓存，
 *       以避免资源泄漏。关闭时抛出的 {@link IOException} 会被包装为 {@link RuntimeException} 抛出。
 *       这与字面语义“移除”略有不同，但强制保证了资源释放。</li>
 *   <li>如果希望显式处理 I/O 异常，请使用 {@link #closeCache(String)} 或 {@link #closeAllCaches()}，
 *       它们也会移除并关闭缓存，但会直接抛出 {@link IOException}。</li>
 *   <li>为了向后兼容，保留 {@code remove} / {@code removeAll} 方法，但其实际行为是“移除并关闭”，
 *       不再提供“只移除不关闭”的 API – 因为该模式极易导致资源泄漏。</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * CacheManager cm = new DefaultCacheManager();
 * cm.register("users", new DefaultCache<>());
 *
 * // 获取缓存实例，通过泛型直接拿到期望类型
 * Cache<Integer, User> userCache = cm.getCache("users");
 * User user = userCache.get(1001);
 *
 * // 清空缓存条目（保留缓存实例）
 * cm.clearCache("users");
 *
 * // 应用关闭时释放所有缓存（方式1：推荐，closeAllCaches 声明异常）
 * cm.closeAllCaches();
 *
 * // 或（方式2：removeAll 不会声明 IOException，但会包装后抛出 RuntimeException）
 * cm.removeAll();
 * }</pre>
 *
 * <p><b>类型安全约定：</b>
 * {@link #getCache(String)} 被设计为泛型方法，可通过变量类型声明或显式泛型参数
 * 获得期望的缓存类型，无需额外传递 {@link Class} 对象。
 * 类型正确性由调用者保证，类型错误会在运行时以 {@link ClassCastException} 暴露。
 * </p>
 *
 * <p><b>线程安全性：</b> 所有实现都必须是线程安全的，允许多线程并发调用。</p>
 *
 * @author wsp
 * @version 1.0
 * @see Cache
 */
public interface CacheManager extends Closeable {

    // ==================== 注册与移除（移除即关闭，避免泄漏） ====================

    /**
     * 注册一个缓存实例。
     * <p>
     * 如果同名缓存已存在，则本次注册会被忽略，返回 {@code false}，原有缓存不会被替换或关闭。
     * 如需替换，请先调用 {@link #removeCache(String)} 或 {@link #closeCache(String)} 移除旧缓存，
     * 再注册新缓存。
     * </p>
     *
     * @param name  缓存名称，用于后续检索，不能为 {@code null} 或空字符串
     * @param cache 缓存实例，不能为 {@code null}
     * @return 注册成功返回 {@code true}，若名称已存在则返回 {@code false}
     * @throws NullPointerException 如果任一参数为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    boolean register(String name, Cache<?, ?> cache);

    /**
     * 移除并返回指定名称的缓存实例，<b>同时关闭该缓存</b>。
     * <p>
     * <b>注意：</b> 此方法在实现中会强制调用缓存的 {@link Cache#close()} 方法，
     * 以避免资源（数据库连接、文件句柄等）泄漏。关闭时如果抛出 {@link IOException}，
     * 会将其包装为 {@link RuntimeException} 并抛出（因为接口方法未声明该异常）。
     * </p>
     * <p>
     * 如果你希望显式处理 I/O 异常，请使用 {@link #closeCache(String)}，该方法直接抛出
     * {@link IOException}，且同样会移除并关闭缓存。
     * </p>
     *
     * @param name 缓存名称，不能为 {@code null}
     * @return 被移除的缓存实例（已关闭），若名称不存在则返回 {@code null}
     * @throws NullPointerException  如果 {@code name} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭，或关闭缓存时发生 I/O 错误（包装后）
     */
    Cache<?, ?> removeCache(String name);

    /**
     * 移除所有已注册的缓存，<b>同时关闭它们</b>。
     * <p>
     * <b>注意：</b> 此方法会逐个关闭每个缓存，若某个缓存关闭时抛出 {@link IOException}，
     * 则会在处理完所有缓存后，将第一个异常包装为 {@link RuntimeException} 并抛出，
     * 但会保证每个缓存都尝试关闭。
     * </p>
     * <p>
     * 如需显式处理 I/O 异常，请使用 {@link #closeAllCaches()}，该方法直接声明
     * {@link IOException} 并同样执行关闭和移除。
     * </p>
     *
     * @throws IllegalStateException 如果管理器已关闭，或至少一个缓存关闭失败（包装后）
     */
    void removeAll();

    // ==================== 清理操作 ====================

    /**
     * 清空指定缓存中的所有条目（保留缓存实例及其配置）。
     * <p>
     * 该操作委托给对应缓存实例的 {@link Cache#clear()} 方法。
     * 缓存实例本身及其容量、过期策略等配置均保持不变。
     * </p>
     *
     * @param name 缓存名称，不能为 {@code null}
     * @throws NullPointerException  如果 {@code name} 为 {@code null}
     * @throws IllegalArgumentException 如果指定名称的缓存不存在
     * @throws IllegalStateException 如果管理器已关闭
     */
    void clearCache(String name);

    /**
     * 清空所有已注册缓存中的条目。
     * <p>
     * 该操作会逐个调用每个缓存的 {@link Cache#clear()} 方法。
     * 缓存实例本身及配置不受影响。
     * </p>
     *
     * @throws IllegalStateException 如果管理器已关闭
     */
    void clearAllCaches();

    // ==================== 关闭操作 ====================

    /**
     * 关闭并移除指定名称的缓存实例（显式抛出 I/O 异常）。
     * <p>
     * 如果缓存存在，则先调用其 {@link Cache#close()} 方法释放资源，
     * 然后将该缓存从管理器中移除。关闭后缓存不可再用。
     * 若指定名称不存在，则什么也不做。
     * </p>
     * <p>
     * 此方法与 {@link #removeCache(String)} 的行为一致（都是移除并关闭），
     * 区别在于此方法直接声明 {@link IOException}，便于调用方显式处理；
     * 而 {@code remove} 会将异常包装为 {@link RuntimeException}。
     * </p>
     *
     * @param name 缓存名称，不能为 {@code null}
     * @throws IOException          可能由底层缓存的关闭操作抛出
     * @throws NullPointerException 如果 {@code name} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    void closeCache(String name) throws IOException;

    /**
     * 关闭并移除所有已注册的缓存实例（显式抛出 I/O 异常）。
     * <p>
     * 该方法会逐个调用每个缓存的 {@link Cache#close()} 方法释放资源，
     * 并清空注册表。调用后管理器回到初始状态，可以继续注册新缓存。
     * 如果某个缓存关闭失败，会在处理完所有缓存后抛出第一个遇到的异常。
     * </p>
     * <p>
     * 此方法与 {@link #removeAll()} 的行为一致，区别在于异常处理方式：
     * 此方法直接抛出 {@link IOException}，而 {@code removeAll} 会包装为 {@link RuntimeException}。
     * </p>
     *
     * @throws IOException          可能由某个缓存的关闭操作抛出
     * @throws IllegalStateException 如果管理器已关闭
     */
    void closeAllCaches() throws IOException;

    // ==================== 监控与统计 ====================

    /**
     * 返回当前已注册的缓存数量。
     *
     * @return 缓存数量
     * @throws IllegalStateException 如果管理器已关闭
     */
    int getCacheCount();

    // ==================== 辅助方法 ====================

    /**
     * 检查指定名称的缓存是否已注册。
     *
     * @param name 缓存名称，不能为 {@code null}
     * @return 如果已注册则返回 {@code true}，否则返回 {@code false}
     * @throws NullPointerException  如果 {@code name} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    boolean contains(String name);

    /**
     * 返回所有已注册缓存的名称集合（不可变快照）。
     * <p>
     * 返回的集合仅反映调用时刻的注册状态，后续注册/移除不会影响该集合。
     * </p>
     *
     * @return 包含所有缓存名称的 {@link Set}
     * @throws IllegalStateException 如果管理器已关闭
     */
    Set<String> getAllCacheNames();

    // ==================== 核心访问方法 ====================

    /**
     * 根据名称获取缓存实例。
     * <p>
     * 此方法为泛型方法，可通过变量类型声明或显式泛型参数直接获得期望的缓存类型：
     * <pre>{@code
     * Cache<Integer, User> cache = manager.getCache("users");
     * // 或
     * Cache<Integer, User> cache = manager.<Integer, User>getCache("users");
     * }</pre>
     * <b>类型安全：</b> 管理器内部不存储类型信息，返回的缓存类型与注册时的实际类型无关。
     * 调用者须确保键值类型与预期一致，否则后续存取操作可能抛出 {@link ClassCastException}。
     * </p>
     *
     * @param name 缓存名称，不能为 {@code null}
     * @param <K>  期望的键类型（由调用者指定）
     * @param <V>  期望的值类型（由调用者指定）
     * @return 对应的缓存实例，若名称不存在则返回 {@code null}
     * @throws NullPointerException  如果 {@code name} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    <K, V> Cache<K, V> getCache(String name);
}