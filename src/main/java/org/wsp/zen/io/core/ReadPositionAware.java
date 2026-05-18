package org.wsp.zen.io.core;

/**
 * 可感知当前读取位置的接口。
 * <p>
 * 实现该接口的输入流（或读取器）能够返回从数据源开始已读取的字节数，
 * 即当前读取位置的偏移量。常见用于需要在解析过程中记录数据块起始位置、
 * 判断剩余数据长度或实现位置标记/重置的场景。
 * </p>
 *
 * <p><b>典型实现：</b>
 * {@link org.wsp.zen.io.impl.PositionTrackingInputStream} 基于此接口
 * 在每次读取操作后自动累积位置计数，并支持通过 {@code mark/reset} 恢复位置。
 * </p>
 *
 * <p><b>用法示例：</b>
 * <pre>{@code
 * ReadPositionAware stream = new PositionTrackingInputStream(rawStream);
 * // ... 读取数据 ...
 * long currentPos = stream.getPosition();
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see org.wsp.zen.io.impl.PositionTrackingInputStream
 */
public interface ReadPositionAware {

    /**
     * 返回从开始读取到当前位置总共已读取的字节数。
     *
     * @return 已读取的字节数（从 0 开始计数）
     */
    long getPosition();
}