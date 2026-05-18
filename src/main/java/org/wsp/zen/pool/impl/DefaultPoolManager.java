package org.wsp.zen.pool.impl;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.wsp.zen.gif.util.CloseState;
import org.wsp.zen.pool.core.ObjectPool;
import org.wsp.zen.pool.core.PoolManager;

/**
 * {@link PoolManager} 的线程安全默认实现。
 * <p>
 * 内部使用双层 {@link ConcurrentHashMap} 组织池注册表：
 * <ul>
 *   <li>外层键为对象类型 {@link Class}，</li>
 *   <li>内层键为池名称 {@link String}。</li>
 * </ul>
 * 配合 {@link CloseState} 保证管理器关闭后所有操作都被安全拒绝。
 * </p>
 *
 * <h3>线程安全</h3>
 * <ul>
 *   <li>所有单个操作通过 {@link ConcurrentHashMap} 的原子方法保证线程安全。</li>
 *   <li>批量清空、重置、关闭操作使用弱一致性遍历，不会抛出
 *       {@link java.util.ConcurrentModificationException}。</li>
 *   <li>{@link #closeAll()} 等方法先快照类型键，再按类型关闭，避免并发修改导致不一致。</li>
 * </ul>
 *
 * <h3>注册策略（名称唯一性）</h3>
 * <p>
 * 与 {@link org.wsp.zen.cache.impl.DefaultCacheManager} 保持一致，
 * {@link #register(Class, String, ObjectPool)} 采用防覆盖策略：
 * 若已有相同类型和名称的池存在，则忽略本次注册并返回 {@code false}。
 * 如需替换已注册的池，请先调用 {@link #unregisterPool(Class, String)}
 * （仅取消注册不关闭）或 {@link #closePool(Class, String)}（取消注册并关闭），
 * 再执行注册。这避免了静默的资源泄漏和状态混乱。
 * </p>
 *
 * <h3>注意事项</h3>
 * 本实现不参与对象的借用与归还，仅作为池实例的集中注册中心。
 * 池自身的线程安全性由其具体实现（如 {@link DefaultObjectPool}）保证。
 *
 * @author wsp
 * @version 1.1
 * @see ObjectPool
 * @see PoolManager
 */
public class DefaultPoolManager implements PoolManager {

    /** 池注册表：类型 → (名称 → 池) */
    private final Map<Class<?>, Map<String, ObjectPool<?>>> registry = new ConcurrentHashMap<>();

    /** 管理器生命周期状态 */
    private final CloseState closeState = new CloseState("对象池管理器");

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
     * @param type 池中对象的类型令牌，不能为 {@code null}
     * @param name 池的唯一名称，不能为 {@code null}
     * @param pool 对象池实例，不能为 {@code null}
     * @param <T>  池中对象的类型
     * @return 首次注册返回 {@code true}；若名称已存在返回 {@code false}
     * @throws NullPointerException  如果任一参数为 {@code null}
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public <T> boolean register(Class<T> type, String name, ObjectPool<T> pool) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Objects.requireNonNull(name, "池名称不能为 null");
        Objects.requireNonNull(pool, "对象池不能为 null");
        Map<String, ObjectPool<?>> inner = registry.computeIfAbsent(type, k -> new ConcurrentHashMap<>());
        // 使用 putIfAbsent 保证名称唯一，防止覆盖已有池
        return inner.putIfAbsent(name, pool) == null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ObjectPool<T> getPool(Class<T> type, String name) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Objects.requireNonNull(name, "池名称不能为 null");
        Map<String, ObjectPool<?>> inner = registry.get(type);
        if (inner == null) {
            return null;
        }
        return (ObjectPool<T>) inner.get(name);
    }

    // ==================== 移除操作（仅取消注册，不关闭池） ====================

    @Override
    public ObjectPool<?> removePool(Class<?> type, String name) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Objects.requireNonNull(name, "池名称不能为 null");
        Map<String, ObjectPool<?>> inner = registry.get(type);
        if (inner == null) {
            return null;
        }
        ObjectPool<?> pool = inner.remove(name);
        if (inner.isEmpty()) {
            registry.remove(type);
        }
        return pool;
    }

    @Override
    public void unregisterPool(Class<?> type, String name) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Objects.requireNonNull(name, "池名称不能为 null");
        Map<String, ObjectPool<?>> inner = registry.get(type);
        if (inner != null) {
            inner.remove(name);
            if (inner.isEmpty()) {
                registry.remove(type);
            }
        }
    }

    @Override
    public void removeType(Class<?> type) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        registry.remove(type);
    }

    @Override
    public void removeAllPools() {
        closeState.checkClosed();
        registry.clear();
    }

    // ==================== 清空操作 ====================

    @Override
    public void clearPool(Class<?> type, String name) {
        ObjectPool<?> pool = getRequiredPool(type, name);
        pool.clear();
    }

    @Override
    public void clearType(Class<?> type) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Map<String, ObjectPool<?>> inner = registry.get(type);
        if (inner != null) {
            inner.values().forEach(ObjectPool::clear);
        }
    }

    @Override
    public void clearAll() {
        closeState.checkClosed();
        registry.values().forEach(inner -> inner.values().forEach(ObjectPool::clear));
    }

    // ==================== 重置操作 ====================

    @Override
    public void resetPool(Class<?> type, String name) {
        ObjectPool<?> pool = getRequiredPool(type, name);
        pool.reset();
    }

    @Override
    public void resetType(Class<?> type) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Map<String, ObjectPool<?>> inner = registry.get(type);
        if (inner != null) {
            inner.values().forEach(ObjectPool::reset);
        }
    }

    @Override
    public void resetAll() {
        closeState.checkClosed();
        registry.values().forEach(inner -> inner.values().forEach(ObjectPool::reset));
    }

    // ==================== 关闭操作（永久失效） ====================

    @Override
    public void closePool(Class<?> type, String name) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Objects.requireNonNull(name, "池名称不能为 null");
        Map<String, ObjectPool<?>> inner = registry.get(type);
        if (inner != null) {
            ObjectPool<?> pool = inner.remove(name);
            if (pool != null) {
                pool.close();
            }
            if (inner.isEmpty()) {
                registry.remove(type);
            }
        }
    }

    @Override
    public void closeType(Class<?> type) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Map<String, ObjectPool<?>> inner = registry.remove(type);
        if (inner != null) {
            inner.values().forEach(ObjectPool::close);
        }
    }

    @Override
    public void closeAll() {
        closeState.checkClosed();
        for (Class<?> type : Set.copyOf(registry.keySet())) {
            closeType(type);
        }
    }

    // ==================== 监控与辅助 ====================

    @Override
    public int getPoolCount() {
        closeState.checkClosed();
        int count = 0;
        for (Map<String, ObjectPool<?>> inner : registry.values()) {
            count += inner.size();
        }
        return count;
    }

    @Override
    public boolean containsPool(Class<?> type, String name) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Objects.requireNonNull(name, "池名称不能为 null");
        Map<String, ObjectPool<?>> inner = registry.get(type);
        return inner != null && inner.containsKey(name);
    }

    @Override
    public Set<String> getPoolNames(Class<?> type) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Map<String, ObjectPool<?>> inner = registry.get(type);
        if (inner == null) {
            return Set.of();
        }
        return Set.copyOf(inner.keySet());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取已注册的池，若未找到则抛出 {@link IllegalArgumentException}。
     */
    private <T> ObjectPool<T> getRequiredPool(Class<T> type, String name) {
        closeState.checkClosed();
        Objects.requireNonNull(type, "类型令牌不能为 null");
        Objects.requireNonNull(name, "池名称不能为 null");
        ObjectPool<T> pool = getPool(type, name);
        if (pool == null) {
            throw new IllegalArgumentException(
                    "未注册的池: " + type.getSimpleName() + ":\"" + name + "\"");
        }
        return pool;
    }
}