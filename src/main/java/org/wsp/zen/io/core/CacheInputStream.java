package org.wsp.zen.io.core;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * 带磁盘缓存的输入流包装器，在读取原始输入流的同时将数据写入缓存文件。
 * <p>
 * 该类继承 {@link FilterInputStream}，用于从底层输入流读取数据，同时将读取到的所有字节
 * 写入指定的缓存文件。当流被完全读取（到达末尾）时，会在关闭时向缓存文件末尾写入一个
 * 固定的结束标记（{@value #CACHE_MARKER_STRING}）。通过 {@link #isCacheValid(Path)}
 * 方法可以验证缓存文件是否完整有效，从而避免重复下载或解码。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>
 * InputStream netStream = url.openStream();
 * Path cachePath = Paths.get("/cache/temp.cache");
 * try (CacheInputStream cacheStream = new CacheInputStream(netStream, cachePath)) {
 *     // 读取数据（例如 GIF 解码器）
 *     byte[] data = cacheStream.readAllBytes();
 * }
 * // 缓存文件现在包含完整数据 + 结束标记
 * boolean valid = CacheInputStream.isCacheValid(cachePath);
 * </pre>
 *
 * <p><b>线程安全性：</b>
 * 此类的大部分读取方法是同步的（{@code synchronized}），但 {@link #skip(long)} 未显式同步，
 * 其内部依赖同步的 {@code read} 方法，因此整体上可以保证单线程使用的安全。不建议多线程并发使用。
 * </p>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>不支持 {@link #mark(int)} 和 {@link #reset()} 操作。</li>
 *   <li>必须显式调用 {@link #close()} 以确保缓存标记被正确写入且输出流关闭。</li>
 *   <li>如果读取过程中发生异常（未到达末尾），关闭时不会写入结束标记，缓存被视为无效。</li>
 * </ul>
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see #isCacheValid(Path)
 */
public class CacheInputStream extends FilterInputStream {

    // ==================== 缓存完整性标记常量 ====================

    /** 缓存完整性标记字符串（UTF-8 编码） */
    private static final String CACHE_MARKER_STRING = "GIF_END_MARK";
    private static final byte[] CACHE_MARKER = CACHE_MARKER_STRING.getBytes(StandardCharsets.UTF_8);

    // ==================== 实例字段 ====================

    /** 缓存文件输出流 */
    private final OutputStream out;

    /** 是否已完整读取到流末尾 */
    private boolean isComplete = false;

    // ==================== 构造器 ====================

    /**
     * 构造一个缓存输入流。
     *
     * @param in   原始输入流（例如网络流、文件流），不能为 {@code null}
     * @param path 缓存文件路径，不能为 {@code null}。文件将被创建或覆盖
     * @throws IOException 如果无法创建缓存文件或打开输出流
     */
    public CacheInputStream(InputStream in, Path path) throws IOException {
        super(in);
        Objects.requireNonNull(path, "缓存路径不能为 null");
        this.out = new BufferedOutputStream(new FileOutputStream(path.toFile()));
    }

    // ==================== 读取操作（同步，确保写入缓存） ====================

    /**
     * 读取一个字节，并将该字节写入缓存文件。
     *
     * @return 读取的字节（0-255），或 -1 表示流结束
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            out.write(b);
        }
        isComplete = (b == -1);
        return b;
    }

    /**
     * 读取多个字节到数组中，并将读取的字节写入缓存文件。
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
     * 读取指定长度的字节到数组的指定位置，并将读取的字节写入缓存文件。
     *
     * @param b   目标字节数组
     * @param off 起始偏移量
     * @param len 最大读取长度
     * @return 实际读取的字节数，或 -1 表示流结束
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
        int bytesRead = super.read(b, off, len);
        if (bytesRead != -1) {
            out.write(b, off, bytesRead);
        }
        isComplete = (bytesRead == -1);
        return bytesRead;
    }

    /**
     * 读取所有剩余字节并写入缓存文件。
     *
     * @return 包含所有剩余字节的数组
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized byte[] readAllBytes() throws IOException {
        byte[] allBytes = super.readAllBytes();
        out.write(allBytes);
        isComplete = true;
        return allBytes;
    }

    /**
     * 读取最多 {@code len} 个字节并写入缓存文件。
     *
     * @param len 要读取的最大字节数
     * @return 包含读取字节的数组（长度可能小于 len）
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized byte[] readNBytes(int len) throws IOException {
        byte[] nBytes = super.readNBytes(len);
        out.write(nBytes);
        isComplete = (nBytes.length < len);
        return nBytes;
    }

    /**
     * 读取最多 {@code len} 个字节到数组的指定位置，并写入缓存文件。
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
        out.write(b, off, bytesRead);
        isComplete = (bytesRead < len);
        return bytesRead;
    }

    // ==================== 跳跃与可用性 ====================

    /**
     * 跳过并丢弃 n 个字节（同时写入缓存）。
     * <p>
     * 实现方式为读取并写入缓冲区，因此不会提升跳过性能，但保证了缓存完整性。
     *
     * @param n 要跳过的字节数
     * @return 实际跳过的字节数
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        byte[] buffer = new byte[(int) Math.min(4096, n)];
        long skipped = 0;
        int len;
        while (skipped < n) {
            len = read(buffer, 0, (int) Math.min(buffer.length, n - skipped));
            if (len == -1) {
                break;
            }
            skipped += len;
        }
        return skipped;
    }

    /**
     * 精确跳过 n 个字节，如果无法跳过则抛出异常。
     *
     * @param n 要跳过的字节数
     * @throws EOFException 如果流提前结束
     * @throws IOException  如果发生其他 I/O 错误
     */
    @Override
    public void skipNBytes(long n) throws IOException {
        if (n <= 0) {
            return;
        }
        long skipped = skip(n);
        if (skipped < n) {
            throw new EOFException("无法跳过 " + n + " 个字节，仅跳过了 " + skipped + " 个字节（流已结束）");
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

    // ==================== 资源关闭与缓存标记写入 ====================

    /**
     * 关闭流，写入缓存完整性标记（如果已完整读取），并释放资源。
     * <p>
     * 关闭顺序：先关闭底层输入流，然后向缓存文件写入结束标记（如果 {@code isComplete} 为 true），
     * 最后关闭输出流。即使输入流关闭时抛出异常，输出流仍会被关闭。
     * </p>
     *
     * @throws IOException 如果关闭过程中发生错误
     */
    @Override
    public synchronized void close() throws IOException {
        try {
            super.close();
        } finally {
            try {
                if (isComplete) {
                    out.write(CACHE_MARKER);
                }
                out.flush();
            } finally {
                out.close();
            }
        }
    }

    // ==================== 标记/重置（不支持） ====================

    /**
     * 标记操作不支持。
     *
     * @param readlimit 忽略
     * @throws UnsupportedOperationException 总是抛出
     */
    @Override
    public synchronized void mark(int readlimit) {
        throw new UnsupportedOperationException("输入流不支持标记");
    }

    /**
     * 重置操作不支持。
     *
     * @throws IOException 总是抛出
     */
    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("输入流不支持重置");
    }

    /**
     * 是否支持标记操作。
     *
     * @return 始终返回 false
     */
    @Override
    public boolean markSupported() {
        return false;
    }

    // ==================== 数据传输 ====================

    /**
     * 将当前流的所有剩余字节传输到目标输出流，同时写入缓存。
     *
     * @param destination 目标输出流
     * @return 传输的字节数
     * @throws IOException 如果发生 I/O 错误
     */
    @Override
    public synchronized long transferTo(OutputStream destination) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        long transferred = 0;
        while ((bytesRead = read(buffer)) != -1) {
            destination.write(buffer, 0, bytesRead);
            transferred += bytesRead;
        }
        return transferred;
    }

    // ==================== 静态缓存验证方法 ====================

    /**
     * 验证指定缓存文件是否完整有效。
     * <p>
     * 有效性检查基于文件末尾是否存在 {@value #CACHE_MARKER_STRING} 标记。
     * 如果文件存在且长度足够，并且末尾字节与标记匹配，则返回 true。
     *
     * @param cachePath 缓存文件路径
     * @return 如果缓存文件存在且完整有效，则返回 {@code true}；否则返回 {@code false}
     */
    public static boolean isCacheValid(Path cachePath) {
        if (!Files.exists(cachePath)) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(cachePath.toFile(), "r")) {
            long length = raf.length();
            if (length < CACHE_MARKER.length) {
                return false;
            }
            raf.seek(length - CACHE_MARKER.length);
            byte[] end = new byte[CACHE_MARKER.length];
            raf.readFully(end);
            return Arrays.equals(end, CACHE_MARKER);
        } catch (Exception e) {
            return false;
        }
    }
}