package org.wsp.zen.mapping.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

import org.wsp.zen.concurrent.core.ReentrantLockManager;
import org.wsp.zen.concurrent.impl.DefaultReentrantLockManager;
import org.wsp.zen.io.util.IOExceptionUtil;
import org.wsp.zen.gif.util.CloseState;
import org.wsp.zen.mapping.core.BufferedMapping;
import org.wsp.zen.mapping.core.FileMappingManager;
import org.wsp.zen.mapping.core.SegmentManager;
import org.wsp.zen.mapping.exception.MappingMismatchException;
import org.wsp.zen.mapping.model.CompositeReadOperation;
import org.wsp.zen.mapping.model.MappingContext;
import org.wsp.zen.mapping.model.Segment;
import org.wsp.zen.mapping.model.WindowChange;
import org.wsp.zen.mapping.util.MappingValidateUtils;

/**
 * 默认的文件映射管理器实现，组合了窗口片段管理器（{@link SegmentManager}）和
 * 缓冲区管理器（{@link BufferedMapping}），提供线程安全的窗口重映射、数据读取及资源清理功能。
 * <p>
 * 该类通过读写锁（由 {@link ReentrantLockManager} 提供）控制并发访问：
 * <ul>
 *   <li>写操作（如 {@link #remapWindow(MappingContext)}、{@link #clear()}、{@link #close()}）使用写锁，保证互斥。</li>
 *   <li>读操作（如 {@link #read(long, int)} 系列）使用读锁，允许并发读取，提升性能。</li>
 * </ul>
 * </p>
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>通过 {@link Builder} 创建实例，指定文件路径字符串（平台无关）。</li>
 *   <li>调用 {@link #remapWindow(MappingContext)} 建立初始内存映射窗口。</li>
 *   <li>使用 {@link #read(long, int)} 或 {@link #readWithAutoRecovery(...)} 读取数据。</li>
 *   <li>当文件滑动窗口需要变化时，再次调用 {@link #remapWindow}，内部会通过窗口片段管理器计算变更并应用到缓冲区管理器。</li>
 *   <li>使用完毕调用 {@link #close()} 释放所有资源。</li>
 * </ol>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 所有公开方法均通过读写锁保护，读操作可并发执行，写操作互斥，保证内部状态一致。
 * 关闭后任何操作都将抛出 {@link IllegalStateException}。
 * </p>
 *
 * @author wsp
 * @version 1.1
 * @see FileMappingManager
 * @see SegmentManager
 * @see BufferedMapping
 * @see ReentrantLockManager
 */
public class DefaultFileMappingManager implements FileMappingManager {

    /** 窗口片段管理器，负责根据读取请求计算需要映射的窗口片段集合 */
    private SegmentManager segmentManager;

    /** 缓冲区管理器，负责实际的内存映射和读取操作 */
    private BufferedMapping bufferedManager;

    /** 并发控制器（读写锁），保证线程安全 */
    private ReentrantLockManager lockManager;

    /** 生命周期状态管理器，防止关闭后使用 */
    private final CloseState closeState = new CloseState("内存文件映射");

    // ==================== 构造器与 Builder ====================

    /**
     * 私有构造器，通过 Builder 创建实例。
     *
     * @param builder 构建器实例
     * @throws IOException 如果初始化过程中发生 I/O 错误
     */
    private DefaultFileMappingManager(Builder builder) throws IOException {
        this.segmentManager = builder.segmentManager;
        this.bufferedManager = builder.bufferedManager;
        this.lockManager = builder.lockManager;
    }

    /**
     * 构建器，用于创建 {@link DefaultFileMappingManager} 实例。
     * <p>
     * 必须提供文件路径（用于创建默认的 {@link DefaultBufferedMapping}），
     * 其他组件可以自定义。
     * </p>
     */
    public static class Builder {
        private BufferedMapping bufferedManager;
        private SegmentManager segmentManager = new DefaultSegmentManager();
        private ReentrantLockManager lockManager = new DefaultReentrantLockManager();

        /**
         * 基于文件路径字符串构造构建器。
         * 内部转换为 {@link Path} 以利用 NIO 内存映射（仅桌面端），
         * 但对外只暴露平台无关的 {@link String}。
         *
         * @param filePath 文件路径字符串，不能为 {@code null}
         * @throws IOException              如果无法打开文件或创建缓冲区管理器失败
         * @throws NullPointerException     如果 {@code filePath} 为 {@code null}
         */
        public Builder(String filePath) throws IOException {
            Objects.requireNonNull(filePath, "文件路径不能为 null");
            this.bufferedManager = new DefaultBufferedMapping(filePath);
        }

        /**
         * 直接使用已有 {@link BufferedMapping} 实例构造。
         * 适用于已经自定义缓冲区实现的情况。
         *
         * @param bufferedManager 缓冲区管理器，不能为 {@code null}
         */
        public Builder(BufferedMapping bufferedManager) {
            this.bufferedManager = Objects.requireNonNull(bufferedManager, "缓冲区管理器不能为 null");
        }

        /**
         * 构建最终的管理器实例。
         *
         * @return 新的管理器实例
         * @throws IOException 如果构建过程中发生 I/O 错误
         */
        public DefaultFileMappingManager build() throws IOException {
            return new DefaultFileMappingManager(this);
        }

        /**
         * 设置自定义的窗口片段管理器。
         *
         * @param segmentManager 窗口片段管理器，不能为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder withWindowSegmentManager(SegmentManager segmentManager) {
            this.segmentManager = Objects.requireNonNull(segmentManager, "窗口片段管理器不能为 null");
            return this;
        }

        /**
         * 设置自定义的缓冲区管理器。
         *
         * @param bufferedManager 缓冲区管理器，不能为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder withBufferedMapping(BufferedMapping bufferedManager) {
            this.bufferedManager = Objects.requireNonNull(bufferedManager, "缓冲区管理器不能为 null");
            return this;
        }

        /**
         * 设置自定义的并发控制器。
         *
         * @param lockManager 并发控制器，不能为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder withReentrantLockManager(ReentrantLockManager lockManager) {
            this.lockManager = Objects.requireNonNull(lockManager, "并发控制器不能为 null");
            return this;
        }
    }

    // ==================== 窗口重映射 ====================

    /**
     * 重新映射窗口，根据请求参数调整当前内存映射窗口。
     * <p>
     * 该方法会获取写锁，通过窗口片段管理器计算需要新增和保留的片段，
     * 然后将变更应用到缓冲区管理器。
     * </p>
     *
     * @param mappingContext 窗口映射请求，不能为 {@code null}
     * @throws IOException              如果重映射过程中发生 I/O 错误
     * @throws NullPointerException     如果 {@code mappingContext} 为 {@code null}
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public void remapWindow(MappingContext mappingContext) throws IOException {
        closeState.checkClosed();
        Objects.requireNonNull(mappingContext, "窗口映射请求不能为 null");

        try {
            lockManager.withWriteLock(() ->
                IOExceptionUtil.wrapAction(() -> {
                    closeState.checkClosed();
                    // 若请求中文件大小为 -1，则从缓冲区获取实际文件大小
                    long actualSize = mappingContext.availableFileSize != -1 ? mappingContext.availableFileSize : size();
                    MappingContext fullContext = mappingContext.availableFileSize != -1 ? mappingContext : mappingContext.withAvailableFileSize(actualSize);
                    WindowChange changes = segmentManager.updateAndGetChanges(fullContext);
                    bufferedManager.applyChanges(changes);
                })
            );
        } catch (UncheckedIOException e) {
            // 发生异常时尝试清理资源，避免脏状态
            try {
                clear();
            } catch (IOException cleanupEx) {
                e.getCause().addSuppressed(cleanupEx);
            }
            throw new IOException("重新映射窗口失败，映射请求：" + mappingContext, e.getCause());
        }
    }

    // ==================== 基本读取操作（需要窗口已覆盖） ====================

    /**
     * 从指定偏移量读取数据，返回新字节数组。
     * <p>
     * 此方法假定当前映射窗口已经覆盖了所需区域。如果未覆盖，可能抛出异常或返回不完整数据。
     * 推荐使用 {@link #readWithAutoRecovery} 自动恢复。
     * </p>
     *
     * @param startOffset 起始偏移量（≥ 0）
     * @param length      读取长度（> 0）
     * @return 读取的字节数组，如果请求范围超出文件末尾则返回 {@code null}
     * @throws IOException              如果读取过程中发生 I/O 错误
     * @throws IllegalArgumentException 如果参数范围非法
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public byte[] read(long startOffset, int length) throws IOException {
        closeState.checkClosed();
        MappingValidateUtils.validateRangeArguments(startOffset, length, size());

        try {
            return lockManager.withReadLock(() ->
                IOExceptionUtil.wrapSupplier(() -> {
                    closeState.checkClosed();
                    CompositeReadOperation compositeRead = segmentManager.get(startOffset, length);
                    if (compositeRead == null) {
                        return null;
                    }
                    return bufferedManager.read(compositeRead);
                })
            );
        } catch (MappingMismatchException e) {
            handleDirtySegmentAndRetry(e.getDirtySegment());
            return null;
        } catch (UncheckedIOException e) {
            throw new IOException("读取数据失败，起始偏移量：" + startOffset + "，读取长度：" + length, e.getCause());
        }
    }

    /**
     * 从指定偏移量读取数据，并写入目标数组。
     *
     * @param startOffset  起始偏移量（≥ 0）
     * @param length       读取长度（> 0）
     * @param target       目标字节数组，不能为 {@code null}
     * @param targetOffset 目标数组中的起始位置
     * @return 实际读取的字节数（通常等于 length），如果请求范围超出文件末尾则返回 -1
     * @throws IOException              如果读取过程中发生 I/O 错误
     * @throws NullPointerException     如果 {@code target} 为 {@code null}
     * @throws IllegalArgumentException 如果参数范围非法
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public int read(long startOffset, int length, byte[] target, int targetOffset) throws IOException {
        closeState.checkClosed();
        MappingValidateUtils.validateReadBuffer(startOffset, length, target, targetOffset, size());

        try {
            return lockManager.withReadLock(() ->
                IOExceptionUtil.wrapSupplier(() -> {
                    closeState.checkClosed();
                    CompositeReadOperation compositeRead = segmentManager.get(startOffset, length);
                    if (compositeRead == null) {
                        return -1;
                    }
                    bufferedManager.read(compositeRead, target, targetOffset);
                    return length;
                })
            );
        } catch (MappingMismatchException e) {
            handleDirtySegmentAndRetry(e.getDirtySegment());
            return -1;
        } catch (UncheckedIOException e) {
            throw new IOException("读取数据失败，起始偏移量：" + startOffset + "，读取长度：" + length, e.getCause());
        }
    }

    // ==================== 带自动恢复的读取操作 ====================

    /**
     * 带自动恢复的读取操作，返回新字节数组。
     * <p>
     * 当读取失败（如窗口未覆盖）时，会尝试使用写锁重新映射窗口后重试。
     * </p>
     *
     * @param startOffset    起始偏移量
     * @param length         读取长度
     * @param mappingContext 窗口映射请求参数，用于恢复
     * @return 读取的字节数组（可能为 {@code null}）
     * @throws IOException              如果读取或重映射过程中发生 I/O 错误
     * @throws NullPointerException     如果 {@code mappingContext} 为 {@code null}
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public byte[] readWithAutoRecovery(long startOffset, int length, MappingContext mappingContext) throws IOException {
        closeState.checkClosed();

        byte[] firstResult = read(startOffset, length);
        if (firstResult != null) {
            return firstResult;
        }

        try {
            return lockManager.withWriteLock(() ->
                IOExceptionUtil.wrapSupplier(() -> {
                    closeState.checkClosed();
                    byte[] lockedResult = read(startOffset, length);
                    if (lockedResult != null) {
                        return lockedResult;
                    }
                    remapWindow(mappingContext);
                    return read(startOffset, length);
                })
            );
        } catch (UncheckedIOException e) {
            throw new IOException("无法读取数据，起始偏移量：" + startOffset + "，读取长度：" + length + "，映射请求：" + mappingContext, e.getCause());
        }
    }

    /**
     * 带自动恢复的读取操作，结果写入目标数组。
     *
     * @param startOffset    起始偏移量
     * @param length         读取长度
     * @param target         目标字节数组
     * @param targetOffset   目标数组起始位置
     * @param mappingContext 窗口映射请求参数
     * @return 实际读取的字节数（通常等于 length），如果请求范围超出文件末尾则返回 -1
     * @throws IOException              如果读取或重映射过程中发生 I/O 错误
     * @throws NullPointerException     如果 {@code target} 或 {@code mappingContext} 为 {@code null}
     * @throws IllegalArgumentException 如果参数范围非法
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public int readWithAutoRecovery(long startOffset, int length, byte[] target, int targetOffset, MappingContext mappingContext) throws IOException {
        closeState.checkClosed();

        int firstResult = read(startOffset, length, target, targetOffset);
        if (firstResult != -1) {
            return length;
        }

        try {
            return lockManager.withWriteLock(() ->
                IOExceptionUtil.wrapSupplier(() -> {
                    closeState.checkClosed();
                    int lockedResult = read(startOffset, length, target, targetOffset);
                    if (lockedResult != -1) {
                        return length;
                    }
                    remapWindow(mappingContext);
                    return read(startOffset, length, target, targetOffset);
                })
            );
        } catch (UncheckedIOException e) {
            throw new IOException("无法读取数据，起始偏移量：" + startOffset + "，读取长度：" + length + "，映射请求：" + mappingContext, e.getCause());
        }
    }

    // ==================== 状态查询 ====================

    /**
     * 获取文件总大小（字节）。
     *
     * @return 文件大小
     * @throws IOException           如果无法获取文件大小
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public long size() throws IOException {
        closeState.checkClosed();
        return bufferedManager.size();
    }

    // ==================== 清理与关闭 ====================

    /**
     * 关闭管理器，释放所有资源。
     * <p>
     * 调用后管理器不可再用。重复调用无副作用。
     * </p>
     *
     * @throws IOException 如果关闭过程中发生 I/O 错误
     */
    @Override
    public void close() throws IOException {
        try {
            lockManager.withWriteLock(() ->
                IOExceptionUtil.wrapAction(() -> {
                    closeState.checkClosed();
                    closeState.markAsClosed();
                    bufferedManager.close();
                    segmentManager.close();
                })
            );
        } catch (UncheckedIOException e) {
            throw new IOException("关闭资源失败", e.getCause());
        }
    }

    /**
     * 清除所有映射状态，释放当前窗口片段。
     *
     * @throws IOException           如果清除过程中发生 I/O 错误
     * @throws IllegalStateException 如果管理器已关闭
     */
    @Override
    public void clear() throws IOException {
        closeState.checkClosed();
        lockManager.withWriteLock(() ->
            IOExceptionUtil.wrapAction(() -> {
                closeState.checkClosed();
                bufferedManager.clear();
                segmentManager.clear();
            })
        );
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 处理脏片段：从片段管理器和缓冲区管理器中移除指定的窗口片段。
     * <p>
     * 当读取时发生 {@link MappingMismatchException}，说明某片段可能已损坏或无效，
     * 调用此方法将其清理，以便后续重试。
     * </p>
     *
     * @param dirtySegment 需要清理的脏窗口片段
     * @throws IOException 如果清理过程中发生 I/O 错误
     */
    private void handleDirtySegmentAndRetry(Segment dirtySegment) throws IOException {
        closeState.checkClosed();
        lockManager.withWriteLock(() ->
            IOExceptionUtil.wrapAction(() -> {
                closeState.checkClosed();
                boolean removed = segmentManager.removeSegment(dirtySegment);
                if (removed) {
                    bufferedManager.release(dirtySegment);
                    System.out.println("系统已自动清理脏片段：" + dirtySegment);
                }
            })
        );
    }
}