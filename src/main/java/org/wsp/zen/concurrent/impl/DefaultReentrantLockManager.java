package org.wsp.zen.concurrent.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.wsp.zen.concurrent.core.ReentrantLockManager;
import org.wsp.zen.concurrent.core.LockedAction;
import org.wsp.zen.concurrent.core.LockedSupplier;

/**
 * 默认的并发控制器实现，基于 {@link ReentrantReadWriteLock} 提供读写锁机制。
 * <p>
 * 该类适用于需要区分读操作（可并发）和写操作（互斥）的场景，例如帧缓存的重建窗口（写锁）
 * 与数据读取（读锁）。读锁允许多个线程同时持有，显著提升并发读性能；写锁提供互斥访问，
 * 保证数据一致性。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>
 * DefaultReentrantLockManager controller = new DefaultReentrantLockManager();
 * 
 * // 写操作示例
 * controller.withWriteLock(() -> {
 *     // 更新共享数据
 * });
 * 
 * // 读操作示例
 * String result = controller.withReadLock(() -> {
 *     return sharedData.toString();
 * });
 * 
 * // 尝试获取写锁（非阻塞或超时）
 * if (controller.tryWriteLock(1, TimeUnit.SECONDS)) {
 *     try {
 *         // 临界区代码
 *     } finally {
 *         controller.releaseWriteLock();
 *     }
 * }
 * </pre>
 *
 * <p><b>锁升级/降级：</b>
 * 本实现不支持从读锁升级到写锁（会导致死锁）。如果需要升级，应释放读锁后再获取写锁。
 * 支持从写锁降级到读锁（通过先获取读锁再释放写锁）。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see ReentrantLockManager
 * @see ReentrantReadWriteLock
 */
public class DefaultReentrantLockManager implements ReentrantLockManager {

    // 重建窗口使用写锁，读取数据使用读锁（提升并发读性能）
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    // ==================== 写锁委托执行 ====================

    /**
     * 在写锁保护下执行给定的动作（无返回值）。
     *
     * @param action 需要在线程安全（写锁）下执行的动作，不能为 {@code null}
     * @throws NullPointerException 如果 {@code action} 为 {@code null}
     * @throws RuntimeException 如果 {@code action.execute()} 抛出未检查异常，锁会被正确释放
     */
    @Override
    public void withWriteLock(LockedAction action) {
        boolean lockAcquired = false;

        try {
            writeLock.lock();
            lockAcquired = true;
            action.execute();
        } finally {
            if (lockAcquired) {
                writeLock.unlock();
            }
        }
    }

    /**
     * 在写锁保护下执行给定的动作并返回结果。
     *
     * @param action 需要在线程安全（写锁）下执行的动作，不能为 {@code null}
     * @param <T>    返回结果的类型
     * @return 动作执行的结果
     * @throws NullPointerException 如果 {@code action} 为 {@code null}
     * @throws RuntimeException 如果 {@code action.execute()} 抛出未检查异常，锁会被正确释放
     */
    @Override
    public <T> T withWriteLock(LockedSupplier<T> action) {
        boolean lockAcquired = false;

        try {
            writeLock.lock();
            lockAcquired = true;
            return action.execute();
        } finally {
            if (lockAcquired) {
                writeLock.unlock();
            }
        }
    }

    // ==================== 读锁委托执行 ====================

    /**
     * 在读锁保护下执行给定的动作并返回结果。
     * <p>
     * 多个线程可以同时持有读锁，适合只读操作。
     * </p>
     *
     * @param action 需要在线程安全（读锁）下执行的动作，不能为 {@code null}
     * @param <T>    返回结果的类型
     * @return 动作执行的结果
     * @throws NullPointerException 如果 {@code action} 为 {@code null}
     * @throws RuntimeException 如果 {@code action.execute()} 抛出未检查异常，锁会被正确释放
     */
    @Override
    public <T> T withReadLock(LockedSupplier<T> action) {
        boolean lockAcquired = false;

        try {
            readLock.lock();
            lockAcquired = true;
            return action.execute();
        } finally {
            if (lockAcquired) {
                readLock.unlock();
            }
        }
    }

    /**
     * 在读锁保护下执行给定的动作（无返回值）。
     *
     * @param action 需要在线程安全（读锁）下执行的动作，不能为 {@code null}
     * @throws NullPointerException 如果 {@code action} 为 {@code null}
     * @throws RuntimeException 如果 {@code action.execute()} 抛出未检查异常，锁会被正确释放
     */
    @Override
    public void withReadLock(LockedAction action) {
        boolean lockAcquired = false;

        try {
            readLock.lock();
            lockAcquired = true;
            action.execute();
        } finally {
            if (lockAcquired) {
                readLock.unlock();
            }
        }
    }

    // ==================== 尝试获取锁 ====================

    /**
     * 尝试在指定超时时间内获取写锁。
     *
     * @param timeout 等待锁的最大时间
     * @param unit    时间单位
     * @return {@code true} 如果成功获取到写锁，否则 {@code false}
     * @throws InterruptedException 如果当前线程在等待时被中断
     */
    @Override
    public boolean tryWriteLock(long timeout, TimeUnit unit) throws InterruptedException {
        return writeLock.tryLock(timeout, unit);
    }

    /**
     * 尝试在指定超时时间内获取读锁。
     *
     * @param timeout 等待锁的最大时间
     * @param unit    时间单位
     * @return {@code true} 如果成功获取到读锁，否则 {@code false}
     * @throws InterruptedException 如果当前线程在等待时被中断
     */
    @Override
    public boolean tryReadLock(long timeout, TimeUnit unit) throws InterruptedException {
        return readLock.tryLock(timeout, unit);
    }

    // ==================== 手动释放锁 ====================

    /**
     * 释放当前线程持有的写锁。
     *
     * @throws IllegalMonitorStateException 如果当前线程并不持有写锁
     */
    @Override
    public void releaseWriteLock() {
        if (!isWriteLockedByCurrentThread()) {
            throw new IllegalMonitorStateException("当前线程不持有写锁");
        }
        writeLock.unlock();
    }

    /**
     * 释放当前线程持有的读锁。
     *
     * @throws IllegalMonitorStateException 如果当前线程并不持有读锁
     */
    @Override
    public void releaseReadLock() {
        if (!isReadLockedByCurrentThread()) {
            throw new IllegalMonitorStateException("当前线程不持有读锁");
        }
        readLock.unlock();
    }

    // ==================== 锁状态查询 ====================

    /**
     * 检查当前线程是否持有写锁。
     *
     * @return {@code true} 如果当前线程持有写锁，否则 {@code false}
     */
    public boolean isWriteLockedByCurrentThread() {
        return ((ReentrantReadWriteLock) rwLock).isWriteLockedByCurrentThread();
    }

    /**
     * 检查当前线程是否持有读锁。
     * <p>
     * 注意：一个线程可以多次获取读锁（可重入），只要持有次数大于0即视为持有读锁。
     * </p>
     *
     * @return {@code true} 如果当前线程持有至少一个读锁，否则 {@code false}
     */
    public boolean isReadLockedByCurrentThread() {
        return ((ReentrantReadWriteLock) rwLock).getReadHoldCount() > 0;
    }
}