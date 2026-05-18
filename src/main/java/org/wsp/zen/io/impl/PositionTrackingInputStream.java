package org.wsp.zen.io.impl;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.wsp.zen.io.core.ReadPositionAware;

/**
 * 带位置跟踪的输入流包装器，记录当前已读取的字节数。
 * <p>
 * 该类继承 {@link FilterInputStream}，在读取数据的同时维护一个内部计数器（{@code position}），
 * 表示从流开始到现在已读取的字节总数。支持 {@link #mark(int)} 和 {@link #reset()} 操作，
 * 重置时会恢复位置计数器到标记时的值。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>
 * InputStream rawStream = Files.newInputStream(Paths.get("file.bin"));
 * PositionTrackingInputStream tracker = new PositionTrackingInputStream(rawStream);
 * 
 * // 读取一些数据
 * byte[] header = tracker.readNBytes(10);
 * System.out.println("当前位置: " + tracker.getPosition()); // 输出 10
 * 
 * // 标记位置
 * tracker.mark(100);
 * byte[] data = tracker.readNBytes(50);
 * System.out.println("当前位置: " + tracker.getPosition()); // 输出 60
 * 
 * // 重置回标记点
 * tracker.reset();
 * System.out.println("重置后位置: " + tracker.getPosition()); // 输出 10
 * </pre>
 *
 * <p><b>用途：</b>
 * 适用于需要知道精确读取位置的场景，例如解析 GIF 等二进制格式时记录数据块偏移量，
 * 或用于调试和验证流读取进度。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see FilterInputStream
 */
public class PositionTrackingInputStream extends FilterInputStream implements ReadPositionAware {

    // ==================== 字段 ====================

    /** 当前读取位置（已读取的字节数） */
    private long position = 0;

    /** 标记时的位置（用于 {@link #reset()}） */
    private long markPosition = 0;

    // ==================== 构造器 ====================

    /**
     * 构造一个位置跟踪输入流。
     *
     * @param in 底层输入流，不能为 {@code null}
     */
    public PositionTrackingInputStream(InputStream in) {
        super(in);
    }

    // ==================== 读取操作（同步，更新位置） ====================

    /**
     * 读取一个字节，并更新位置计数器。
     *
     * @return 读取的字节（0-255），或 -1 表示流结束
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            position++;
        }
        return b;
    }

    /**
     * 读取多个字节到数组中，并更新位置计数器。
     *
     * @param b 目标字节数组
     * @return 实际读取的字节数，或 -1 表示流结束
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * 读取指定长度的字节到数组的指定位置，并更新位置计数器。
     *
     * @param b   目标字节数组
     * @param off 起始偏移量
     * @param len 最大读取长度
     * @return 实际读取的字节数，或 -1 表示流结束
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
        int readLen = super.read(b, off, len);
        if (readLen != -1) {
            position += readLen;
        }
        return readLen;
    }

    /**
     * 读取所有剩余字节，并更新位置计数器。
     *
     * @return 包含所有剩余字节的数组
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized byte[] readAllBytes() throws IOException {
        byte[] allBytes = super.readAllBytes();
        position += allBytes.length;
        return allBytes;
    }

    /**
     * 读取最多 {@code len} 个字节，并更新位置计数器。
     *
     * @param len 要读取的最大字节数
     * @return 包含读取字节的数组（长度可能小于 len）
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized byte[] readNBytes(int len) throws IOException {
        byte[] nBytes = super.readNBytes(len);
        position += nBytes.length;
        return nBytes;
    }

    /**
     * 读取最多 {@code len} 个字节到数组的指定位置，并更新位置计数器。
     *
     * @param b   目标字节数组
     * @param off 起始偏移量
     * @param len 最大读取长度
     * @return 实际读取的字节数
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized int readNBytes(byte[] b, int off, int len) throws IOException {
        int bytesRead = super.readNBytes(b, off, len);
        position += bytesRead;
        return bytesRead;
    }

    // ==================== 跳跃与可用性 ====================

    /**
     * 跳过并丢弃 n 个字节，并更新位置计数器。
     *
     * @param n 要跳过的字节数
     * @return 实际跳过的字节数
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized long skip(long n) throws IOException {
        long skipped = super.skip(n);
        position += skipped;
        return skipped;
    }

    /**
     * 精确跳过 n 个字节，如果无法跳过则抛出异常，并更新位置计数器。
     *
     * @param n 要跳过的字节数（不能为负数）
     * @throws IllegalArgumentException 如果 {@code n < 0}
     * @throws EOFException             如果流提前结束
     * @throws IOException              如果发生其他 I/O 错误
     */
    @Override
    public void skipNBytes(long n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException("n 不能为负数");
        }
        if (n == 0) {
            return;
        }
        long skipped = skip(n);
        if (skipped < n) {
            throw new EOFException("流已结束，无法跳过 " + n + " 字节");
        }
    }

    /**
     * 返回底层输入流的可用字节数估计值。
     *
     * @return 可用字节数估计值
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public int available() throws IOException {
        return super.available();
    }

    // ==================== 关闭 ====================

    /**
     * 关闭底层输入流。
     *
     * @throws IOException 如果关闭时发生错误
     */
    @Override
    public synchronized void close() throws IOException {
        super.close();
    }

    // ==================== 标记与重置 ====================

    /**
     * 标记当前读取位置，以便后续通过 {@link #reset()} 返回。
     * <p>
     * 同时保存当前的位置计数器值到内部标记变量。
     *
     * @param readlimit 标记失效前可读取的最大字节数（由底层流决定是否遵守）
     */
    @Override
    public synchronized void mark(int readlimit) {
        super.mark(readlimit);
        markPosition = position;
    }

    /**
     * 重置流到最近的标记位置，同时恢复位置计数器。
     *
     * @throws IOException 如果底层流不支持重置或重置失败
     */
    @Override
    public synchronized void reset() throws IOException {
        super.reset();
        position = markPosition;
    }

    /**
     * 返回底层输入流是否支持标记操作。
     *
     * @return {@code true} 如果底层流支持标记，否则 {@code false}
     */
    @Override
    public boolean markSupported() {
        return super.markSupported();
    }

    // ==================== 位置查询 ====================

    /**
     * 获取当前已读取的字节数（即读取位置）。
     *
     * @return 从流开始到当前位置的字节数
     */
    @Override
    public long getPosition() {
        return position;
    }
}