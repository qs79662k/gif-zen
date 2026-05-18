package org.wsp.zen.pool.impl;

import org.wsp.zen.pool.core.ObjectFactory;

/**
 * {@code int[]} 的默认对象工厂实现，支持动态学习数组长度（线程安全）。
 * <p>
 * 该工厂不预设数组长度，而是在首次归还对象时通过 {@link #reset(int[])} 学习实际长度，
 * 后续 {@link #create()} 将按学习到的最新长度生成整型数组。
 * </p>
 *
 * <p><b>长度学习策略：</b>
 * 只会将内部记录的长度扩大（取历史归还数组中的最大值），不会缩小。
 * 这样设计可以满足多变长度需求，同时避免反复创建不同大小数组导致池失效和内存浪费。
 * </p>
 *
 * <p><b>使用说明：</b>
 * <ul>
 *   <li>在池初始状态或从未归还过数组时，{@link #create()} 返回 {@code null}，调用方需自行创建实例并归还。</li>
 *   <li>调用 {@link #reset(int[])} 时，工厂会从归还的数组中提取长度并更新内部记录（线程安全）。</li>
 *   <li>数组没有需要显式释放的系统资源，因此无需实现 {@code destroy} 方法（接口已提供默认空实现）。</li>
 *   <li>
 *     <b>重置工厂状态：</b> 当需要丢弃已学习的最大长度（如加载新文件）时，调用 {@link #clear()}
 *     可将记录重置为初始值（-1）。此方法应由对象池在确保无外借对象时调用，以避免并发风险。
 *   </li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 使用 {@code volatile} 保证可见性。{@link #reset(int[])} 与 {@link #clear()}
 * 均采用 {@code synchronized} 保护复合操作，避免并发更新导致数据错误。
 * {@link #create()} 通过将 volatile 值一次性读取到局部变量的方式，
 * 消除了 TOCTOU 问题（两次读取之间可能被 {@code clear()} 改变），无需加锁，保持高并发性能。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see ObjectFactory
 */
public class DefaultIntArrayFactory implements ObjectFactory<int[]> {

    // ==================== 可变状态 ====================

    /** 当前记录的最大数组长度，-1 表示未初始化 */
    private volatile int currentMaxLength = -1;

    // ==================== 对象创建 ====================

    /**
     * 创建一个新的 {@code int[]} 实例。
     * <p>
     * 只有当工厂已通过学习获得有效长度（{@code length > 0}）时，
     * 才会创建对应长度的整型数组；否则返回 {@code null}。
     * </p>
     * <p>
     * <b>并发安全：</b> 使用局部变量一次性捕获 {@code volatile} 字段的快照，
     * 避免在判断通过之后、创建数组之前，另一线程调用 {@link #clear()} 导致长度为 -1
     * 而抛出 {@link NegativeArraySizeException}。局部变量 copy 后不受外部修改影响。
     * </p>
     *
     * @return 新创建的整型数组，若长度尚未初始化则返回 {@code null}
     */
    @Override
    public int[] create() {
        int length = currentMaxLength;
        if (length <= 0) {
            return null;
        }
        return new int[length];
    }

    // ==================== 对象重置与长度学习 ====================

    /**
     * 重置并学习数组的长度信息（线程安全）。
     * <p>
     * 从归还的数组中提取长度，如果大于当前记录的长度，则更新记录。
     * 这样后续创建的数组至少能容纳当前归还数组的大小。
     * </p>
     * <p><b>注意：</b> 本方法不进行数组内容清零，如需清零请在调用方自行处理或重写本方法补充。
     * </p>
     *
     * @param obj 归还的 {@code int[]} 实例，可以为 {@code null}（若为 {@code null} 则直接返回）
     */
    @Override
    public synchronized void reset(int[] obj) {
        if (obj == null) {
            return;
        }
        int objLength = obj.length;
        if (objLength > currentMaxLength) {
            currentMaxLength = objLength;
        }
    }

    // ==================== 状态清除 ====================

    /**
     * 清除工厂的内部状态，将记录的最大长度重置为初始值（-1）。
     * <p>
     * 此方法用于丢弃已学习的最大数组长度，通常在需要切换工作负载（如加载新文件）时调用。
     * 调用后，{@link #create()} 将再次返回 {@code null}，直到通过 {@link #reset(int[])}
     * 重新学习新长度。
     * </p>
     * <p>
     * <b>线程安全性：</b> 该方法使用 {@code synchronized} 修饰，与 {@link #reset(int[])}
     * 互斥执行，避免并发重置导致状态不一致。
     * </p>
     * <p>
     * <b>调用限制：</b> 此方法应在对象池确保无外借对象（即所有对象已归还）时调用，
     * 否则可能造成正在外部使用的数组与工厂记录不一致，导致未定义行为。
     * </p>
     */
    @Override
    public synchronized void clear() {
        currentMaxLength = -1;
    }
    
    // ==================== 对象有效性验证 ====================

    /**
     * 验证整型数组是否满足最小长度要求。
     * <p>
     * 只有当数组长度不小于当前已学习的最大长度时，才返回 {@code true}。
     * 避免短数组占用池空间，保持池内对象统一为高容量。
     * </p>
     *
     * @param obj 需要验证的整型数组，由池保证不为 {@code null}
     * @return {@code true} 如果数组长度 ≥ 当前记录的最大长度
     */
    @Override
    public boolean validate(int[] obj) {
        return obj.length >= currentMaxLength;
    }
}