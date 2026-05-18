package org.wsp.zen.mapping.impl;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.wsp.zen.gif.util.CloseState;
import org.wsp.zen.mapping.core.BufferedMapping;
import org.wsp.zen.mapping.exception.MappingMismatchException;
import org.wsp.zen.mapping.model.CompositeReadOperation;
import org.wsp.zen.mapping.model.Segment;
import org.wsp.zen.mapping.model.SegmentReadOperation;
import org.wsp.zen.mapping.model.WindowChange;
import org.wsp.zen.mapping.util.MappingValidateUtils;

/**
 * 基于 {@link FileChannel} 和 {@link MappedByteBuffer} 的默认文件窗口映射实现。
 * <p>
 * 提供对文件指定窗口片段的内存映射、读取和资源管理功能。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 本类<strong>不是线程安全的</strong>。多线程环境下的安全使用必须由上层调用者负责同步。
 * 具体约束如下：
 * <ul>
 *   <li><b>读操作</b>（如 {@link #read(Segment, int, byte[], int, int)}）：
 *       {@link MappedByteBuffer#get} 本身是线程安全的，因此多个线程可以并发执行读取，
 *       前提是映射窗口集合在读取期间不发生变更。</li>
 *   <li><b>写操作</b>（如 {@link #map(Segment)}、{@link #release(Segment)}、
 *       {@link #applyChanges(WindowChange)}、{@link #clear()}、{@link #close()}）：
 *       必须由外部进行同步（例如使用同一把锁），避免并发修改映射缓存导致状态不一致。</li>
 *   <li>{@link #applyChanges(WindowChange)} 方法自身使用 {@code synchronized} 关键字
 *       保证“释放旧片段 + 映射新片段”的原子性，但这不足以保护与其他外部写操作的并发冲突。</li>
 *   <li>推荐的上层同步策略：使用 {@code ReentrantReadWriteLock}，读操作获取读锁，写操作获取写锁。
 *       例如 {@link DefaultFileMappingManager} 中的做法。</li>
 * </ul>
 * </p>
 *
 * <p><b>内存释放：</b>
 * 由于 {@link MappedByteBuffer} 的回收依赖 GC，且可能存在直接内存泄漏风险，该类通过反射调用
 * {@code sun.nio.ch.FileChannelImpl.unmap(MappedByteBuffer)} 或 {@code Cleaner} 来强制释放，
 * 并在降级方案中触发 {@code System.gc()}。这些操作可能需要开启反射访问权限。
 * </p>
 *
 * @author wsp
 * @version 1.1
 * @see BufferedMapping
 * @see MappedByteBuffer
 */
public class DefaultBufferedMapping implements BufferedMapping {

    // ==================== 字段 ====================

    /** 底层文件通道，只读模式打开 */
    private final FileChannel fileChannel;

    /** 窗口片段 → 内存映射缓冲区的映射缓存，使用 ConcurrentHashMap 保证 put/remove 的原子性，但整体状态一致性仍需外部同步 */
    private final Map<Segment, MappedByteBuffer> mappedBuffers = new ConcurrentHashMap<>();

    /** 生命周期状态管理 */
    private final CloseState closeState = new CloseState("文件缓冲区");

    // ==================== 构造器 ====================

    /**
     * 构造一个基于路径的文件映射管理器。
     * <p>
     * 接收平台无关的字符串路径，内部转换为 {@link Path} 后打开只读文件通道。
     * </p>
     *
     * @param filePath 文件路径（平台无关字符串），不能为 {@code null}
     * @throws IOException              如果无法打开文件通道（文件不存在、无读权限等）
     * @throws NullPointerException     如果 {@code filePath} 为 {@code null}
     */
    public DefaultBufferedMapping(String filePath) throws IOException {
        Objects.requireNonNull(filePath, "文件路径不能为 null");
        Path path = Paths.get(filePath);
        this.fileChannel = FileChannel.open(
                path,
                EnumSet.of(StandardOpenOption.READ)
        );
    }

    // ==================== 映射操作 ====================

    /**
     * 将指定的窗口片段映射到内存缓冲区。
     * <p>
     * 如果该片段已经映射，则新映射会替换原有映射（通过 {@code put} 覆盖）。
     * </p>
     * <p><b>线程安全：</b>该方法不是线程安全的，调用者需确保外部同步，避免与其他写操作并发。</p>
     *
     * @param segment 需要映射的窗口片段，不能为 {@code null}
     * @throws IOException              如果映射过程中发生 I/O 错误
     * @throws NullPointerException     如果 {@code segment} 为 {@code null}
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public void map(Segment segment) throws IOException {
        closeState.checkClosed();
        Objects.requireNonNull(segment, "需映射的窗口片段不能为 null");

        MappedByteBuffer mappedBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                segment.startOffset,
                segment.size());
        mappedBuffers.put(segment, mappedBuffer);
    }

    /**
     * 批量将多个窗口片段映射到内存缓冲区。
     * <p>
     * 按顺序依次调用 {@link #map(Segment)}，若某一映射失败，之前成功的映射不会回滚。
     * </p>
     * <p><b>线程安全：</b>该方法不是线程安全的，调用者需确保外部同步。</p>
     *
     * @param segments 需要映射的窗口片段列表，不能为 {@code null}，元素不能为 {@code null}
     * @throws IOException              如果任一映射过程中发生 I/O 错误
     * @throws NullPointerException     如果 {@code segments} 或其中元素为 {@code null}
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public void map(List<Segment> segments) throws IOException {
        closeState.checkClosed();
        for (Segment segment : segments) {
            map(segment);
        }
    }

    /**
     * 映射一组新片段（从 Set 转为 List 后调用批量映射）。
     *
     * @param segmentsToAdd 需要新增的片段集合，不能为 {@code null}
     * @throws IOException 如果映射失败
     */
    private void map(Set<Segment> segmentsToAdd) throws IOException {
        map(new ArrayList<>(segmentsToAdd));
    }

    // ==================== 读取操作 ====================

    /**
     * 从指定的窗口片段中读取一段连续数据到目标字节数组。
     * <p>
     * 读取操作本身是线程安全的（依赖 {@link MappedByteBuffer#get} 的线程安全性），
     * 但前提是该片段在读取期间不会被外部并发释放或重新映射。调用者应通过适当的同步保证映射窗口的稳定性。
     * </p>
     *
     * @param segment       要读取的窗口片段，不能为 {@code null}
     * @param startOffset   片段内的起始偏移量（从 0 开始）
     * @param target        目标字节数组，不能为 {@code null}
     * @param targetOffset  目标数组中的起始位置
     * @param length        需要读取的字节数
     * @throws NullPointerException      如果 {@code segment} 或 {@code target} 为 {@code null}
     * @throws IllegalArgumentException  如果参数范围非法（见接口描述）
     * @throws MappingMismatchException  如果片段未被映射
     * @throws IllegalStateException     如果管理器已关闭
     */
    @Override
    public void read(Segment segment, int startOffset, byte[] target, int targetOffset, int length)
            throws MappingMismatchException {
        closeState.checkClosed();
        Objects.requireNonNull(segment, "窗口片段不能为 null");

        MappingValidateUtils.validateReadBuffer(startOffset, length, target, targetOffset, segment.size());

        MappedByteBuffer mappedBuffer = mappedBuffers.get(segment);
        if (mappedBuffer == null) {
            throw new MappingMismatchException(segment);
        }

        mappedBuffer.get(startOffset, target, targetOffset, length);
    }

    /**
     * 根据复合读取操作，从多个窗口片段中读取数据，并合并成一个完整的字节数组返回。
     * <p>
     * 读取操作的线程安全性要求与 {@link #read(Segment, int, byte[], int, int)} 相同。
     * </p>
     *
     * @param compositeRead 复合读取操作对象，不能为 {@code null}
     * @return 合并后的字节数组，长度等于 {@code compositeRead.totalLength}
     * @throws NullPointerException      如果 {@code compositeRead} 为 {@code null}
     * @throws MappingMismatchException  如果任一片段未映射或读取失败
     * @throws IllegalStateException     如果管理器已关闭
     */
    @Override
    public byte[] read(CompositeReadOperation compositeRead) throws MappingMismatchException {
        closeState.checkClosed();
        Objects.requireNonNull(compositeRead, "复合读取对象不能为 null");

        byte[] resultData = new byte[compositeRead.totalLength];

        for (SegmentReadOperation segmentRead : compositeRead.readOperations) {
            read(
                    segmentRead.segment,
                    segmentRead.startOffset,
                    resultData,
                    segmentRead.targetArrayOffset,
                    segmentRead.getReadLength());
        }

        return resultData;
    }

    /**
     * 根据复合读取操作，从多个窗口片段中读取数据，并将数据直接写入指定的目标字节数组。
     * <p>
     * 读取操作的线程安全性要求与 {@link #read(Segment, int, byte[], int, int)} 相同。
     * </p>
     *
     * @param compositeRead 复合读取操作对象，不能为 {@code null}
     * @param target        目标字节数组，不能为 {@code null}
     * @param targetOffset  目标数组中的起始位置
     * @throws NullPointerException      如果 {@code compositeRead} 或 {@code target} 为 {@code null}
     * @throws IllegalArgumentException  如果目标数组容量不足
     * @throws MappingMismatchException  如果任一片段未映射或读取失败
     * @throws IllegalStateException     如果管理器已关闭
     */
    @Override
    public void read(CompositeReadOperation compositeRead, byte[] target, int targetOffset)
            throws MappingMismatchException {
        closeState.checkClosed();
        Objects.requireNonNull(compositeRead, "复合读取对象不能为 null");

        MappingValidateUtils.validateByteArrayPosition(target, targetOffset, compositeRead.totalLength);

        for (SegmentReadOperation segmentRead : compositeRead.readOperations) {
            read(
                    segmentRead.segment,
                    segmentRead.startOffset,
                    target,
                    segmentRead.targetArrayOffset + targetOffset,
                    segmentRead.getReadLength());
        }
    }

    // ==================== 释放映射 ====================

    /**
     * 释放指定窗口片段的映射资源。
     * <p>
     * <b>线程安全：</b>该方法不是线程安全的，调用者需确保外部同步，避免与其他写操作并发。
     * </p>
     *
     * @param segment 需要释放的窗口片段，不能为 {@code null}
     * @throws NullPointerException     如果 {@code segment} 为 {@code null}
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public void release(Segment segment) {
        closeState.checkClosed();
        Objects.requireNonNull(segment, "需释放的窗口片段不能为 null");

        MappedByteBuffer mappedBuffer = mappedBuffers.get(segment);
        Objects.requireNonNull(mappedBuffer, "未找到片段对应的映射缓冲区，可能已被释放或从未映射：" + segment);
        mappedBuffers.remove(segment);
        cleanupBuffer(mappedBuffer);
    }

    /**
     * 批量释放多个窗口片段的映射资源。
     * <p>
     * <b>线程安全：</b>该方法不是线程安全的，调用者需确保外部同步。
     * </p>
     *
     * @param segments 需要释放的窗口片段列表，不能为 {@code null}，元素不能为 {@code null}
     * @throws NullPointerException     如果 {@code segments} 或其中元素为 {@code null}
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public void release(List<Segment> segments) {
        closeState.checkClosed();
        Objects.requireNonNull(segments, "需释放的窗口片段列表不能为 null");

        for (Segment segment : segments) {
            release(segment);
        }
    }

    // ==================== 窗口变更应用 ====================

    /**
     * 应用窗口片段的变更，先释放不在保留集中的旧片段，再映射新片段。
     * <p>
     * 该方法使用 {@code synchronized} 方法级同步，保证“释放旧片段 + 映射新片段”的原子性，
     * 但<b>这并不使整个类变为线程安全</b>。调用者仍需要确保不同写操作之间以及写操作与读操作之间的正确同步。
     * </p>
     *
     * @param changes 变更对象，不能为 {@code null}
     * @throws IOException              如果映射新片段时发生 I/O 错误
     * @throws NullPointerException     如果 {@code changes} 为 {@code null}
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public synchronized void applyChanges(WindowChange changes) throws IOException {
        closeState.checkClosed();
        Objects.requireNonNull(changes, "变更对象不能为 null");

        releaseSegmentsToRemove(changes.segmentsToRetain);
        map(changes.segmentsToAdd);
    }

    /**
     * 释放所有不在保留集合中的窗口片段映射。
     *
     * @param segmentsToRetain 需要保留的窗口片段集合，不能为 {@code null}
     */
    private void releaseSegmentsToRemove(Set<Segment> segmentsToRetain) {
        List<Segment> segmentsToRemove = new ArrayList<>();

        for (Segment segment : mappedBuffers.keySet()) {
            if (!segmentsToRetain.contains(segment)) {
                segmentsToRemove.add(segment);
            }
        }

        release(segmentsToRemove);
    }

    // ==================== 状态查询 ====================

    /**
     * 校验指定的窗口片段是否已被映射。
     * <p>
     * 该方法仅检查当前缓存状态，不提供一致性保证。在多线程环境下，返回值可能立即过时，
     * 调用者应自行设计同步策略。
     * </p>
     *
     * @param segment 需要检查的窗口片段，不能为 {@code null}
     * @return {@code true} 如果该片段已映射，否则 {@code false}
     * @throws NullPointerException     如果 {@code segment} 为 {@code null}
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public boolean isMapped(Segment segment) {
        closeState.checkClosed();
        Objects.requireNonNull(segment, "查询的窗口片段不能为 null");
        return mappedBuffers.containsKey(segment);
    }

    /**
     * 获取文件总大小（字节）。
     * <p>
     * 此方法直接调用 {@link FileChannel#size()}，该调用本身是线程安全的，
     * 但不保证与当前映射窗口的一致性。
     * </p>
     *
     * @return 文件大小
     * @throws IOException              如果无法获取文件大小（如通道已关闭）
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public long size() throws IOException {
        closeState.checkClosed();
        return fileChannel.size();
    }

    // ==================== 清空与关闭 ====================

    /**
     * 清空所有已映射的片段，释放所有映射资源。
     * <p>
     * <b>线程安全：</b>该方法不是线程安全的，调用者需确保外部同步，避免在清空过程中有并发读取或写入。
     * </p>
     *
     * @throws IllegalStateException    如果管理器已关闭
     */
    @Override
    public void clear() {
        closeState.checkClosed();
        List<Segment> segmentsToRemove = new ArrayList<>(mappedBuffers.keySet());
        release(segmentsToRemove);
    }

    /**
     * 关闭管理器，释放所有资源（文件通道、映射缓冲区）。
     * <p>
     * 此操作是幂等的：多次调用不会有副作用。
     * </p>
     * <p><b>线程安全：</b>该方法不是线程安全的，调用者必须确保关闭期间没有其他线程访问本类实例。</p>
     *
     * @throws IOException 如果关闭文件通道时发生错误
     */
    @Override
    public void close() throws IOException {
        if (closeState.isClosed()) {
            return;
        }

        try {
            clear();
            closeState.markAsClosed();
        } finally {
            if (fileChannel != null && fileChannel.isOpen()) {
                fileChannel.close();
            }
        }
    }

    // ==================== 静态资源清理 ====================

    /**
     * 尝试通过内部 API 强制释放 MappedByteBuffer 占用的直接内存。
     * <p>
     * 首先尝试 {@code sun.nio.ch.FileChannelImpl.unmap}，如果失败则降级为
     * {@code Cleaner} 反射调用，最后触发 {@code System.gc()}。
     * </p>
     *
     * @param buffer 需要释放的缓冲区，可以为 {@code null}
     */
    private static void cleanupBuffer(MappedByteBuffer buffer) {
        if (buffer == null) {
            return;
        }

        try {
            Class<?> fileChannelImplClass = Class.forName("sun.nio.ch.FileChannelImpl");
            Method unmapMethod = fileChannelImplClass.getDeclaredMethod("unmap", MappedByteBuffer.class);
            unmapMethod.setAccessible(true);
            unmapMethod.invoke(null, buffer);
        } catch (ClassNotFoundException e) {
            System.err.println("未找到 FileChannelImpl 类: " + e.getMessage());
        } catch (Exception e) {
            fallbackCleanup(buffer);
        }
    }

    /**
     * 降级清理方法，通过反射调用 Cleaner 或触发 GC。
     *
     * @param buffer 需要释放的缓冲区
     */
    private static void fallbackCleanup(MappedByteBuffer buffer) {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            try {
                Method cleanerMethod = buffer.getClass().getMethod("cleaner");
                cleanerMethod.setAccessible(true);
                Object cleaner = cleanerMethod.invoke(buffer);

                if (cleaner != null) {
                    Method cleanMethod = cleaner.getClass().getMethod("clean");
                    cleanMethod.invoke(cleaner);
                }
            } catch (Exception e) {
                // 最后手段：触发 GC，期望 JVM 回收直接内存
                System.gc();
                System.err.println("警告：无法完全释放 MappedByteBuffer，已触发垃圾回收");
            }
            return null;
        });
    }
}