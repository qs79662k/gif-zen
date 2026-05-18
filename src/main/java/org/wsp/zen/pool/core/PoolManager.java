package org.wsp.zen.pool.core;

import java.util.Set;

/**
 * 对象池管理器，按类型（{@link Class}）和名称（{@link String}）统一管理多个 {@link ObjectPool} 实例。
 * <p>
 * 管理器只负责池实例的注册、查找、移除、清空、重置与关闭，
 * <b>不参与对象的借用与归还</b>。获取到 {@link ObjectPool} 实例后，
 * 由调用者自行调用 {@link ObjectPool#obtain()} 和 {@link ObjectPool#release(Object)}。
 * </p>
 *
 * <h3>操作语义</h3>
 * <ul>
 *   <li><b>remove</b>：仅从管理器中取消注册，<b>不关闭</b>池实例，池仍可独立使用。</li>
 *   <li><b>unregister</b>：与 remove 语义相同，仅取消注册而不关闭池。</li>
 *   <li><b>clear</b>：销毁池中所有空闲对象，但<b>保留工厂已学习的尺寸</b>。后续借用仍按原尺寸创建对象。</li>
 *   <li><b>reset</b>：销毁所有空闲对象，并<b>清除工厂已学习的尺寸</b>，之后需重新学习。</li>
 *   <li><b>close</b>：永久销毁池内所有对象并移除注册，池不可再用。</li>
 * </ul>
 *
 * <h3>注册策略（名称唯一性）</h3>
 * <p>
 * 与 {@code CacheManager} 保持一致，{@link #register(Class, String, ObjectPool)} 采用防覆盖策略：
 * 若已有相同类型和名称的池存在，则忽略本次注册并返回 {@code false}。
 * 如需替换已注册的池，请先调用 {@link #unregisterPool(Class, String)}
 * （仅取消注册不关闭）或 {@link #closePool(Class, String)}（取消注册并关闭），
 * 再执行注册。这避免了静默的资源泄漏和状态混乱。
 * </p>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * PoolManager manager = new DefaultPoolManager();
 * boolean ok = manager.register(byte[].class, "buffer",
 *                  new DefaultObjectPool<>(10, new DefaultByteArrayFactory()));
 * if (!ok) {
 *     // 名称已存在，需要进行替换操作
 *     manager.closePool(byte[].class, "buffer"); // 关闭旧池
 *     manager.register(byte[].class, "buffer", new DefaultObjectPool<>(10, new DefaultByteArrayFactory()));
 * }
 *
 * ObjectPool<byte[]> pool = manager.getPool(byte[].class, "buffer");
 * byte[] buf = pool.obtain();
 * if (buf == null) {
 *     buf = new byte[4096];
 *     pool.release(buf);  // 教会工厂
 *     buf = pool.obtain();
 * }
 * try {
 *     // 使用 buf...
 * } finally {
 *     pool.release(buf);
 * }
 *
 * // 清空空闲对象但保留尺寸
 * manager.clearPool(byte[].class, "buffer");
 *
 * // 重置尺寸学习
 * manager.resetPool(byte[].class, "buffer");
 *
 * // 关闭所有池
 * manager.closeAll();
 * }</pre>
 *
 * <p><b>线程安全性：</b> 所有实现都必须是线程安全的，允许多线程并发调用。</p>
 *
 * @author wsp
 * @version 1.1
 * @see ObjectPool
 */
public interface PoolManager {

    // ==================== 注册与查找 ====================

    /**
     * 注册一个对象池实例。
     * <p>
     * 若同一类型（{@code type}）与名称（{@code name}）的池已存在，
     * 则保留原有池，忽略本次注册并返回 {@code false}。
     * 原有池不会被替换或关闭。如需替换，请先调用 {@link #unregisterPool(Class, String)}
     * 或 {@link #closePool(Class, String)} 移除旧池。
     * </p>
     *
     * @param type 对象类型令牌（如 {@code byte[].class}），不能为 {@code null}
     * @param name 池名称，用于区分同一类型下的不同规格（如 {@code "small"}、{@code "large"}），不能为 {@code null}
     * @param pool 要注册的对象池实例，不能为 {@code null}
     * @param <T>  池中对象的类型
     * @return 首次注册返回 {@code true}；若名称已存在返回 {@code false}
     * @throws NullPointerException  如果任一参数为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    <T> boolean register(Class<T> type, String name, ObjectPool<T> pool);

    /**
     * 根据类型和名称获取已注册的对象池。
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @param name 池名称，不能为 {@code null}
     * @param <T>  池中对象的类型
     * @return 对应的对象池实例，若未注册则返回 {@code null}
     * @throws NullPointerException 如果任一参数为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    <T> ObjectPool<T> getPool(Class<T> type, String name);

    // ==================== 移除操作（仅取消注册，不关闭池） ====================

    /**
     * 移除并返回 <b>单个</b> 已注册的对象池（不关闭该池）。
     * <p>
     * 与 {@link #unregisterPool(Class, String)} 语义相同，仅取消注册而不关闭池。
     * 移除后池实例仍然存活，可继续独立使用。若需要彻底销毁，请调用 {@link #closePool(Class, String)}。
     * </p>
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @param name 池名称，不能为 {@code null}
     * @return 被移除的池实例，若不存在则返回 {@code null}
     * @throws NullPointerException 如果任一参数为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    ObjectPool<?> removePool(Class<?> type, String name);

    /**
     * 仅取消注册指定名称的池，<b>不关闭该池</b>。
     * <p>
     * 此方法与 {@link #removePool(Class, String)} 的唯一区别在于：
     * <b>你不需要拿到被移除的池实例</b>。如果你需要获取被移除的池，请使用 {@code removePool}。
     * 该方法主要用于将池的管理权转移给外部调用者，仅从注册表中移除，不干扰池的生命周期。
     * </p>
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @param name 池名称，不能为 {@code null}
     * @throws NullPointerException 如果任一参数为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     * @since 1.1
     */
    void unregisterPool(Class<?> type, String name);

    /**
     * 移除 <b>指定类型下所有</b> 已注册的池（不关闭它们）。
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @throws NullPointerException 如果 {@code type} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    void removeType(Class<?> type);

    /**
     * 移除 <b>所有</b> 已注册的池（不关闭它们）。
     *
     * @throws IllegalStateException 如果管理器已关闭
     */
    void removeAllPools();

    // ==================== 清空操作（保留工厂尺寸学习结果） ====================

    /**
     * 清空 <b>单个</b> 池中的空闲对象（销毁）。
     * <p>
     * 调用指定池的 {@link ObjectPool#clear()}，销毁池中所有空闲对象，
     * 但<b>保留工厂已经学习到的尺寸</b>（如最大数组长度、图像宽高）。
     * 清空后池为空，但再次 {@link ObjectPool#obtain()} 时工厂仍会按原尺寸创建新对象。
     * </p>
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @param name 池名称，不能为 {@code null}
     * @throws NullPointerException     如果任一参数为 {@code null}
     * @throws IllegalArgumentException 如果未找到指定类型和名称的池
     * @throws IllegalStateException    如果管理器已关闭
     */
    void clearPool(Class<?> type, String name);

    /**
     * 清空 <b>指定类型下所有</b> 池的空闲对象。
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @throws NullPointerException 如果 {@code type} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    void clearType(Class<?> type);

    /**
     * 清空 <b>所有</b> 已注册池的空闲对象。
     *
     * @throws IllegalStateException 如果管理器已关闭
     */
    void clearAll();

    // ==================== 重置操作（清除工厂尺寸学习结果） ====================

    /**
     * 重置 <b>单个</b> 池的学习状态。
     * <p>
     * 操作等同于调用底层池的 {@link ObjectPool#reset()}：
     * 销毁池中所有空闲对象，并且清除工厂已学习到的尺寸信息（如最大数组长度）。
     * 重置后池仍然可用，但再次 {@link ObjectPool#obtain()} 将返回 {@code null}，
     * 直到调用者再次归还一个对象以重新教给工厂合适的尺寸。
     * </p>
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @param name 池名称，不能为 {@code null}
     * @throws NullPointerException     如果任一参数为 {@code null}
     * @throws IllegalArgumentException 如果未找到指定类型和名称的池
     * @throws IllegalStateException    如果管理器已关闭
     */
    void resetPool(Class<?> type, String name);

    /**
     * 重置 <b>指定类型下所有</b> 池的学习状态。
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @throws NullPointerException 如果 {@code type} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    void resetType(Class<?> type);

    /**
     * 重置 <b>所有</b> 已注册池的学习状态。
     *
     * @throws IllegalStateException 如果管理器已关闭
     */
    void resetAll();

    // ==================== 关闭操作（永久失效） ====================

    /**
     * 关闭并移除 <b>单个</b> 池。
     * <p>
     * 调用指定池的 {@link ObjectPool#close()} 释放其内部持有的所有对象，
     * 然后将其从管理器的注册表中移除。关闭后该池不可再用。
     * </p>
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @param name 池名称，不能为 {@code null}
     * @throws NullPointerException 如果任一参数为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    void closePool(Class<?> type, String name);

    /**
     * 关闭并移除 <b>指定类型下所有</b> 的池。
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @throws NullPointerException 如果 {@code type} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    void closeType(Class<?> type);

    /**
     * 关闭并移除 <b>所有</b> 已注册的池。
     * <p>
     * 调用后管理器回到初始状态，可以继续注册新池。
     * </p>
     *
     * @throws IllegalStateException 如果管理器已关闭
     */
    void closeAll();

    // ==================== 监控与辅助方法 ====================

    /**
     * 返回当前已注册的对象池总数（所有类型、所有名称）。
     *
     * @return 对象池总数
     * @throws IllegalStateException 如果管理器已关闭
     */
    int getPoolCount();

    /**
     * 检查指定类型和名称的池是否已注册。
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @param name 池名称，不能为 {@code null}
     * @return 如果已注册则返回 {@code true}，否则返回 {@code false}
     * @throws NullPointerException 如果任一参数为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    boolean containsPool(Class<?> type, String name);

    /**
     * 返回指定类型下所有已注册池的名称集合（不可变快照）。
     *
     * @param type 对象类型令牌，不能为 {@code null}
     * @return 该类型下所有池的名称集合，若该类型未注册任何池则返回空集合
     * @throws NullPointerException 如果 {@code type} 为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    Set<String> getPoolNames(Class<?> type);
}