package org.wsp.zen.mapping.exception;

import org.wsp.zen.mapping.model.Segment;

/**
 * 映射不一致异常，表示窗口片段虽已注册但底层映射缓冲区已丢失或失效。
 * <p>
 * 该异常通常在尝试从内存映射窗口读取数据时抛出，表明请求的窗口片段
 * （{@link Segment}）在管理器中处于已映射状态，但底层的操作系统内存映射
 * 已被意外回收或失效。这可能是由于系统资源紧张、垃圾回收或手动清理导致的。
 * </p>
 *
 * <p><b>异常处理建议：</b>
 * 当捕获此异常时，调用者应尝试重新映射受影响的窗口片段，例如通过
 * {@link org.wsp.zen.mapping.core.MappingManager#map(Segment)} 重新建立映射。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * try {
 *     reader.read(segment, 0, buffer, 0, length);
 * } catch (MappingMismatchException e) {
 *     Segment dirty = e.getDirtySegment();
 *     System.err.println("片段映射失效: " + dirty);
 *     // 重新映射并重试
 *     manager.map(dirty);
 *     reader.read(segment, 0, buffer, 0, length);
 * }
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see Segment
 * @see org.wsp.zen.mapping.core.MappingManager
 * @see org.wsp.zen.mapping.core.SegmentReader
 */
public class MappingMismatchException extends RuntimeException {

    /** 序列化版本 ID，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /** 导致异常的窗口片段，即映射已失效的片段 */
    private final Segment segment;

    /**
     * 构造一个映射不一致异常。
     *
     * @param segment 映射已失效的窗口片段，不能为 {@code null}
     * @throws NullPointerException 如果 {@code segment} 为 {@code null}
     */
    public MappingMismatchException(Segment segment) {
        super("窗口片段存在但映射缓冲区已丢失（脏片段）：" + segment);
        this.segment = segment;
    }

    /**
     * 获取导致异常的窗口片段（脏片段）。
     *
     * @return 映射已失效的片段，不为 {@code null}
     */
    public Segment getDirtySegment() {
        return segment;
    }
}