package org.wsp.zen.pool.impl;

import java.awt.image.BufferedImage;
import org.wsp.zen.pool.core.ObjectFactory;

/**
 * {@link BufferedImage} 的默认对象工厂实现，支持动态学习图像尺寸（线程安全）。
 * <p>
 * 该工厂不预设图像尺寸，而是在首次归还对象时通过 {@link #reset(BufferedImage)} 学习实际宽高，
 * 后续 {@link #create()} 将按学习到的最新尺寸生成图像。
 * </p>
 *
 * <p><b>尺寸学习策略：</b>
 * 只会将内部记录的尺寸扩大（取历史归还对象中的最大值），不会缩小。
 * 这样设计可以满足多变尺寸需求，同时避免反复创建不同尺寸对象导致池失效和内存浪费。
 * </p>
 *
 * <p><b>使用说明：</b>
 * <ul>
 *   <li>在池初始状态或从未归还过对象时，{@link #create()} 返回 {@code null}，调用方需自行创建实例并归还。</li>
 *   <li>调用 {@link #reset(BufferedImage)} 时，工厂会从归还的对象中提取宽高并更新内部记录（线程安全）。</li>
 *   <li>调用 {@link #destroy(BufferedImage)} 会调用 {@code flush()} 释放本地资源。</li>
 *   <li>
 *     <b>重置工厂状态：</b> 当需要丢弃已学习的尺寸（如加载新文件）时，调用 {@link #clear()}
 *     可将宽高记录重置为初始值（-1）。此方法应由对象池在确保无外借对象时调用，以避免并发风险。
 *   </li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 工厂内部维护可变状态（当前最大宽高），使用 {@code volatile} 保证可见性。
 * {@link #reset(BufferedImage)} 与 {@link #clear()} 均采用 {@code synchronized} 保护复合操作，
 * 避免并发更新导致数据错误。
 * {@link #create()} 通过将 volatile 值一次性读取到局部变量的方式，
 * 消除了 TOCTOU 问题（两次读取之间可能被 {@code clear()} 改变），且无需加锁，保持高并发性能。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see ObjectFactory
 * @see BufferedImage
 */
public class DefaultBufferedImageFactory implements ObjectFactory<BufferedImage> {

    // ==================== 可变状态 ====================

    /** 当前记录的最大图像宽度（像素），-1 表示未初始化 */
    private volatile int currentMaxWidth = -1;

    /** 当前记录的最大图像高度（像素），-1 表示未初始化 */
    private volatile int currentMaxHeight = -1;

    // ==================== 对象创建 ====================

    /**
     * 创建一个新的 {@link BufferedImage} 实例。
     * <p>
     * 只有当工厂已通过学习获得有效尺寸（{@code width > 0 && height > 0}）时，
     * 才会创建 {@code TYPE_INT_ARGB} 类型的图像；否则返回 {@code null}。
     * </p>
     * <p>
     * <b>并发安全：</b> 使用局部变量一次性捕获 {@code volatile} 字段的快照，
     * 避免在判断通过后、构造图像前，另一线程调用 {@link #clear()} 导致字段变为 -1
     * 而抛出 {@link IllegalArgumentException}。局部变量 copy 后不受外部修改影响。
     * </p>
     *
     * @return 新创建的图像，若尺寸尚未初始化则返回 {@code null}
     */
    @Override
    public BufferedImage create() {
        int width = currentMaxWidth;
        int height = currentMaxHeight;
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    // ==================== 对象重置与尺寸学习 ====================

    /**
     * 重置并学习对象的尺寸信息（线程安全）。
     * <p>
     * 从归还的图像中提取宽高，如果大于当前记录的尺寸则更新记录。
     * 该方法为 {@code synchronized}，保证并发调用时的原子性。
     * 如果传入 {@code null}，则直接返回。
     * </p>
     *
     * @param obj 归还的 {@link BufferedImage} 实例，可以为 {@code null}
     */
    @Override
    public synchronized void reset(BufferedImage obj) {
        if (obj == null) {
            return;
        }
        int objWidth = obj.getWidth();
        int objHeight = obj.getHeight();

        if (objWidth > currentMaxWidth) {
            currentMaxWidth = objWidth;
        }
        if (objHeight > currentMaxHeight) {
            currentMaxHeight = objHeight;
        }
    }

    // ==================== 状态清除 ====================

    /**
     * 清除工厂的内部状态，将记录的宽高重置为初始值（-1）。
     * <p>
     * 此方法用于丢弃已学习的最大尺寸，通常在需要切换工作负载（如加载新文件）时调用。
     * 调用后，{@link #create()} 将再次返回 {@code null}，直到通过 {@link #reset(BufferedImage)}
     * 重新学习新尺寸。
     * </p>
     * <p>
     * <b>线程安全性：</b> 该方法使用 {@code synchronized} 修饰，与 {@link #reset(BufferedImage)}
     * 互斥执行，避免并发重置导致状态不一致。
     * </p>
     * <p>
     * <b>调用限制：</b> 此方法应在对象池确保无外借对象（即所有对象已归还）时调用，
     * 否则可能造成正在外部使用的对象与工厂记录不一致，导致未定义行为。
     * </p>
     */
    @Override
    public synchronized void clear() {
        currentMaxWidth = -1;
        currentMaxHeight = -1;
    }

    // ==================== 对象销毁 ====================

    /**
     * 销毁给定的 {@link BufferedImage} 对象，释放其占用的本地资源。
     * <p>
     * 调用 {@link BufferedImage#flush()} 释放图像数据占用的内存（包括原生堆内存）。
     * 如果传入 {@code null}，则方法无操作。
     * </p>
     *
     * @param obj 要销毁的图像对象，可以为 {@code null}
     */
    @Override
    public void destroy(BufferedImage obj) {
        if (obj != null) {
            obj.flush();
        }
    }
    
    // ==================== 对象有效性验证 ====================

    /**
     * 验证图像是否满足最小尺寸要求。
     * <p>
     * 只有当图像的宽度和高度均不小于当前已学习的最大尺寸时，才返回 {@code true}。
     * 这样可以防止因为动态画布缩小而将过小的图像放回池中浪费内存，
     * 同时保证池内图像总是能满足后续大帧的需求。
     * </p>
     * <p>
     * <b>线程安全性：</b> 该方法仅读取 {@code volatile} 字段，不加锁，
     * 因此是高性能且线程安全的。
     * </p>
     *
     * @param obj 需要验证的图像，由池保证不为 {@code null}
     * @return {@code true} 如果图像宽高均 ≥ 当前记录的最大值
     */
    @Override
    public boolean validate(BufferedImage obj) {
        int maxW = currentMaxWidth;
        int maxH = currentMaxHeight;
        return obj.getWidth() >= maxW && obj.getHeight() >= maxH;
    }
}