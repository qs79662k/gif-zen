package org.wsp.zen.concurrent.core;

import java.util.concurrent.TimeUnit;

/**
 * 可重入读写锁管理器接口，提供统一的读写锁加锁、释放与执行语义。
 * <p>
 * 该接口将锁的获取与释放封装在函数式操作中，简化调用方的锁使用模式，
 * 同时提供带超时的尝试加锁方法以支持更灵活的并发控制策略。
 * 实现类通常基于 {@link java.util.concurrent.locks.ReentrantReadWriteLock} 或类似锁机制。
 * </p>
 *
 * @author wsp
 * @version 1.0
 */
public interface ReentrantLockManager {

    /**
     * 在写锁保护下执行指定的操作（无返回值）。
     * <p>
     * 方法会获取写锁，执行操作，然后在 {@code finally} 块中释放写锁。
     * </p>
     *
     * @param action 需要写锁保护的操作，不能为 {@code null}
     */
    void withWriteLock(LockedAction action);

    /**
     * 在写锁保护下执行指定的操作，并返回结果。
     * <p>
     * 方法会获取写锁，执行操作并返回其结果，然后在 {@code finally} 块中释放写锁。
     * </p>
     *
     * @param <T>    返回值的类型
     * @param action 需要写锁保护的操作，不能为 {@code null}
     * @return 操作执行的结果
     */
    <T> T withWriteLock(LockedSupplier<T> action);

    /**
     * 在读锁保护下执行指定的操作，并返回结果。
     * <p>
     * 方法会获取读锁，执行操作并返回其结果，然后在 {@code finally} 块中释放读锁。
     * </p>
     *
     * @param <T>    返回值的类型
     * @param action 需要读锁保护的操作，不能为 {@code null}
     * @return 操作执行的结果
     */
    <T> T withReadLock(LockedSupplier<T> action);

    /**
     * 在读锁保护下执行指定的操作（无返回值）。
     * <p>
     * 方法会获取读锁，执行操作，然后在 {@code finally} 块中释放读锁。
     * </p>
     *
     * @param action 需要读锁保护的操作，不能为 {@code null}
     */
    void withReadLock(LockedAction action);

    /**
     * 尝试在指定的超时时间内获取写锁。
     * <p>
     * 若在超时前成功获取写锁则返回 {@code true}，否则返回 {@code false}。
     * 如果线程在等待时被中断，会抛出 {@link InterruptedException}。
     * </p>
     *
     * @param timeout 最大等待时间
     * @param unit    时间单位，不能为 {@code null}
     * @return {@code true} 如果成功获取写锁，{@code false} 如果超时前未获取
     * @throws InterruptedException 如果当前线程在等待时被中断
     */
    boolean tryWriteLock(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 尝试在指定的超时时间内获取读锁。
     * <p>
     * 若在超时前成功获取读锁则返回 {@code true}，否则返回 {@code false}。
     * 如果线程在等待时被中断，会抛出 {@link InterruptedException}。
     * </p>
     *
     * @param timeout 最大等待时间
     * @param unit    时间单位，不能为 {@code null}
     * @return {@code true} 如果成功获取读锁，{@code false} 如果超时前未获取
     * @throws InterruptedException 如果当前线程在等待时被中断
     */
    boolean tryReadLock(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 释放当前线程持有的写锁。
     * <p>
     * 如果当前线程未持有写锁，释放行为由具体实现定义（通常抛出 {@link IllegalMonitorStateException}）。
     * </p>
     */
    void releaseWriteLock();

    /**
     * 释放当前线程持有的读锁。
     * <p>
     * 如果当前线程未持有读锁，释放行为由具体实现定义（通常抛出 {@link IllegalMonitorStateException}）。
     * </p>
     */
    void releaseReadLock();
}