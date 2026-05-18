package org.wsp.zen.cache.core;

import java.util.function.Function;

/**
 * 支持延迟计算并缓存的键值缓存接口，继承自 {@link BasicCache}。
 * <p>
 * 除了基础缓存操作外，提供 {@link #fetchOrCompute(Object, Function)} 方法，
 * 在缓存未命中时通过指定函数计算新值并自动存入缓存，然后返回。
 * 该接口的典型实现应保证一次计算过程的线程安全性，避免重复计算。
 * </p>
 *
 * @param <K> 缓存键的类型
 * @param <V> 缓存值的类型
 * @author wsp
 * @version 1.0
 */
public interface ComputableCache<K, V> extends BasicCache<K, V> {

    /**
     * 根据键获取缓存值，若不存在则通过指定函数计算并存入缓存。
     * <p>
     * 方法的行为等价于：
     * <pre>{@code
     * V value = cache.get(key);
     * if (value == null) {
     *     value = mappingFunction.apply(key);
     *     cache.put(key, value);
     * }
     * return value;
     * }</pre>
     * 实现类应保证在高并发场景下，同一键的计算函数仅执行一次。
     * </p>
     *
     * @param key             缓存键，不能为 {@code null}
     * @param mappingFunction 计算新值的函数，入参为键，不能为 {@code null}
     * @return 缓存中已有的值或通过函数计算得到的新值
     * @throws NullPointerException 如果 {@code key} 或 {@code mappingFunction} 为 {@code null}
     */
    V fetchOrCompute(K key, Function<K, V> mappingFunction);
}