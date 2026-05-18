package org.wsp.zen.pool.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

import org.wsp.zen.gif.util.CloseState;
import org.wsp.zen.pool.core.ObjectFactory;
import org.wsp.zen.pool.core.ObjectPool;

/**
 * 基于 {@link LinkedBlockingQueue} 的默认对象池实现。
 * <p>
 * 该对象池管理一组可复用的对象实例，通过 {@link ObjectFactory} 进行对象的创建、重置和销毁。
 * 支持固定容量，当池为空时 {@link #obtain()} 会创建新对象；当池满时 {@link #release(Object)} 
 * 会销毁多余的对象，从而控制内存占用。
 * </p>
 *
 * <p><b>生命周期与异常处理：</b>
 * <ul>
 *   <li><b>获取对象</b>：优先从池中获取空闲对象，若无则调用工厂创建新对象。</li>
 *   <li><b>归还对象</b>：先调用工厂的 {@link ObjectFactory#reset(Object)} 重置状态并学习尺寸，
 *       然后调用 {@link ObjectFactory#validate(Object)} 检查对象是否满足最小要求。如果验证失败或池已满，
 *       该对象将被直接销毁。如果重置过程中抛出异常，对象也会被销毁并抛出异常。</li>
 *   <li><b>清空池</b>（{@link #clear()}）：销毁池中所有空闲对象，但<b>保留</b>工厂已学习的尺寸信息。</li>
 *   <li><b>重置池</b>（{@link #reset()}）：销毁所有空闲对象，并<b>清除</b>工厂已学习的尺寸，之后需重新学习。</li>
 *   <li><b>关闭池</b>：销毁池中所有存活的对象，释放资源，之后任何操作都会抛出 {@link IllegalStateException}。</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 基于 {@link LinkedBlockingQueue} 实现，入队和出队操作是线程安全的。
 * 关闭操作通过 {@link CloseState} 结合 {@code volatile} 保证状态一致性。
 * {@code clear()} 与 {@code reset()} 方法以安全的方式清空队列并销毁对象，
 * 但由于不阻塞生产者，在并发极高的情况下仍有微小可能产生新的空闲对象；在正常关闭/重置场景下不会造成问题。
 * </p>
 *
 * @param <T> 池中对象的类型
 * @author wsp
 * @version 1.0
 * @see ObjectPool
 * @see ObjectFactory
 */
public class DefaultObjectPool<T> implements ObjectPool<T> {

    // ==================== 字段 ====================

    private final LinkedBlockingQueue<T> availableObjects;
    private final ObjectFactory<T> objectFactory;
    private final CloseState closeState = new CloseState("对象池");

    // ==================== 构造器 ====================

    /**
     * 构造一个指定最大容量的对象池。
     *
     * @param maxCapacity   池的最大容量（最多同时持有的空闲对象数），必须大于 0
     * @param objectFactory 对象工厂，用于创建、重置和销毁对象，不能为 {@code null}
     * @throws IllegalArgumentException 如果 {@code maxCapacity <= 0}
     * @throws NullPointerException     如果 {@code objectFactory} 为 {@code null}
     */
    public DefaultObjectPool(int maxCapacity, ObjectFactory<T> objectFactory) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("对象池容量必须大于 0，当前值：" + maxCapacity);
        }
    
        this.availableObjects = new LinkedBlockingQueue<>(maxCapacity);
        this.objectFactory = Objects.requireNonNull(objectFactory, "对象工厂不能为 null");
    }

    // ==================== 获取对象 ====================

    /**
     * 从池中获取一个对象。
     * <p>
     * 优先返回池中已有的空闲对象；如果池为空，则调用工厂的 {@link ObjectFactory#create()}
     * 创建一个新对象。
     * </p>
     *
     * @return 一个可用的对象，若工厂尚未学习则可能返回 {@code null}
     * @throws IllegalStateException 如果对象池已关闭
     */
    @Override
    public T obtain() {
        closeState.checkClosed();
        T object = availableObjects.poll();
        return object != null ? object : objectFactory.create();
    }

    // ==================== 归还对象 ====================

    /**
     * 将对象归还到池中。
     * <p>
     * 归还流程：
     * <ol>
     *   <li>调用工厂的 {@link ObjectFactory#reset(Object)} 重置对象状态并学习尺寸。</li>
     *   <li>调用工厂的 {@link ObjectFactory#validate(Object)} 验证对象是否满足最小要求（如尺寸足够大）。
     *       如果验证失败，则直接销毁该对象并返回，不放入池中。</li>
     *   <li>如果验证通过，尝试将对象放回池中；若池已满，则调用工厂的 {@code destroy} 销毁对象。</li>
     *   <li>如果重置过程中抛出异常，则立即调用 {@code destroy} 销毁对象，并将异常原样向上抛出，
     *       以保证上层调用者能够感知到对象已损坏且无法继续复用。</li>
     * </ol>
     * </p>
     *
     * @param obj 要归还的对象，不能为 {@code null}
     * @throws NullPointerException  如果 {@code obj} 为 {@code null}
     * @throws IllegalStateException 如果对象池已关闭
     * @throws RuntimeException      如果工厂的 {@code reset} 方法抛出异常（异常将原样抛出）
     */
    @Override
    public void release(T obj) {
        closeState.checkClosed();
        Objects.requireNonNull(obj, "归还的对象不能为 null");

        // 1. 重置对象状态（学习尺寸）
        try {
            objectFactory.reset(obj);
        } catch (RuntimeException e) {
            // 重置失败，对象可能已损坏，直接销毁
            objectFactory.destroy(obj);
            throw e;   // 保留原始类型
        } catch (Exception e) {
            // 防御：包装受检异常
            objectFactory.destroy(obj);
            throw new IllegalStateException("重置对象失败（非预期异常）", e);
        }

        // 2. 验证对象是否满足最小尺寸要求（如长度、宽高）
        if (!objectFactory.validate(obj)) {
            // 对象太小，不再放回池中，直接销毁
            objectFactory.destroy(obj);
            return;
        }

        // 3. 尝试放回缓冲池
        boolean success = availableObjects.offer(obj);
        // 4. 池满时销毁对象
        if (!success) {
            objectFactory.destroy(obj);
        }
    }

    // ==================== 清空池（保留学习状态） ====================

    /**
     * 清空池中所有空闲对象并销毁，但<b>不重置</b>工厂已学习的尺寸。
     * <p>
     * 操作步骤：
     * <ol>
     *   <li>从队列中取出所有空闲对象。</li>
     *   <li>依次调用工厂的 {@link ObjectFactory#destroy(Object)} 释放资源。</li>
     * </ol>
     * 此后池变为空，但工厂仍然保持之前学到的最大尺寸，因此再次 {@link #obtain()} 时仍会
     * 按该尺寸创建对象（若工厂已初始化且返回非空）。
     * </p>
     *
     * @throws IllegalStateException 如果对象池已关闭
     */
    @Override
    public void clear() {
        closeState.checkClosed();
        List<T> allObjects = new ArrayList<>();
        availableObjects.drainTo(allObjects);
        for (T object : allObjects) {
            objectFactory.destroy(object);
        }
    }

    // ==================== 重置池（清除学习状态） ====================

    /**
     * 重置对象池，销毁所有空闲对象并清除工厂学习到的尺寸信息。
     * <p>
     * 操作等同先调用 {@link #clear()}，再调用工厂的 {@link ObjectFactory#clear()}。
     * 重置后工厂回到未初始化状态，下一次 {@link #obtain()} 将返回 {@code null}，
     * 需要调用者归还一个新对象来重新“教会”工厂合适的尺寸。
     * </p>
     *
     * @throws IllegalStateException 如果对象池已关闭
     */
    @Override
    public void reset() {
        closeState.checkClosed();
        // 1. 清空所有空闲对象
        List<T> allObjects = new ArrayList<>();
        availableObjects.drainTo(allObjects);
        for (T object : allObjects) {
            objectFactory.destroy(object);
        }
        // 2. 重置工厂尺寸学习状态
        objectFactory.clear();
    }

    // ==================== 关闭池 ====================

    /**
     * 关闭对象池，释放所有资源。
     * <p>
     * 关闭后，池中所有存活的对象都会被销毁（调用工厂的 {@link ObjectFactory#destroy(Object)}），
     * 并且对象池被标记为已关闭。之后任何对 {@link #obtain()}、{@link #release(Object)}、
     * {@link #clear()}、{@link #reset()} 或 {@link #close()} 的调用都会抛出 {@link IllegalStateException}。
     * </p>
     * <p>
     * 注意：该方法会等待队列中的所有对象被移出并销毁，但不会等待正在被外部使用的对象归还。
     * </p>
     *
     * @throws IllegalStateException 如果对象池已经关闭
     */
    @Override
    public void close() {
        closeState.checkClosed();
        closeState.markAsClosed();

        List<T> allObjects = new ArrayList<>();
        availableObjects.drainTo(allObjects);
        for (T object : allObjects) {
            objectFactory.destroy(object);
        }
    }
}