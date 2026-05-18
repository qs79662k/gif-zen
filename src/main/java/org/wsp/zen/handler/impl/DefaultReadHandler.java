package org.wsp.zen.handler.impl;

import java.io.IOException;
import java.util.Objects;
import java.util.zip.DataFormatException;

import org.wsp.zen.cache.core.Cache;
import org.wsp.zen.gif.model.FrameData;
import org.wsp.zen.gif.model.FrameInfo;
import org.wsp.zen.gif.util.ColorUtils;
import org.wsp.zen.gif.util.SubBlockUtils;
import org.wsp.zen.gif.util.PoolUtils;
import org.wsp.zen.handler.core.Handler;
import org.wsp.zen.handler.exception.ProcessingException;
import org.wsp.zen.handler.model.HandlerContext;
import org.wsp.zen.mapping.core.FileMappingManager;
import org.wsp.zen.mapping.model.MappingContext;
import org.wsp.zen.pool.core.PoolManager;

/**
 * 默认的 GIF 原始帧数据读取处理器（流水线第一阶段）。
 * <p>
 * 支持：
 * <ul>
 *   <li><b>内存模式</b>：帧数据已在 FrameInfo 中，直接使用。</li>
 *   <li><b>文件映射模式</b>：通过 FileMappingManager 读取，并利用对象池复用临时缓冲区。
 *        若池不可用或长度不足，自动降级为新建数组。</li>
 * </ul>
 * 资源管理：
 * <ul>
 *   <li>临时字节缓冲区（压缩数据、颜色表原始字节）通过 {@link PoolUtils} 借出与归还。</li>
 *   <li>返回的 {@link FrameData} 会通过 {@link FrameData#isLocalColorTable} 字段
 *        明确标识颜色表是否为帧独占（局部颜色表），该标志将指导缓存淘汰监听器是否归还颜色表数组。</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b> 本身无状态，依赖的缓存和池需各自保证线程安全。</p>
 *
 * @author wsp
 * @version 1.1
 */
public class DefaultReadHandler implements Handler<FrameData> {

    // ==================== 依赖组件 ====================

    private final FileMappingManager mapperManager;
    private final Cache<Integer, FrameData> frameDataCache;
    private final PoolManager poolManager;

    // ==================== 构造器 ====================

    /**
     * 构造一个完整的读取处理器，支持文件映射和可选的对象池。
     *
     * @param mapperManager 内存文件映射管理器，可为 {@code null}（此时仅内存模式可用）
     * @param frameDataCache 读取帧数据缓存，不能为 {@code null}
     * @param poolManager    对象池管理器，可为 {@code null}
     * @throws NullPointerException 如果 {@code frameDataCache} 为 {@code null}
     */
    public DefaultReadHandler(FileMappingManager mapperManager,
                              Cache<Integer, FrameData> frameDataCache,
                              PoolManager poolManager) {
        this.mapperManager = mapperManager;
        this.frameDataCache = Objects.requireNonNull(frameDataCache, "帧数据缓存不能为 null");
        this.poolManager = poolManager;   // 允许 null
    }

    // ==================== 处理器入口 ====================

    /**
     * 处理读取请求，返回指定帧的原始压缩数据及颜色表。
     * <p>
     * 颜色表来源信息被记录到返回的 {@link FrameData#isLocalColorTable} 字段中：
     * <ul>
     *   <li>{@code true} — 局部颜色表（本帧独享），淘汰时可以归还对象池。</li>
     *   <li>{@code false} — 全局颜色表（多帧共享），淘汰时<b>不能归还</b>，否则会破坏其他帧的渲染数据。</li>
     * </ul>
     *
     * @param context 处理器上下文
     * @return 包含压缩数据和颜色表（及来源标识）的 FrameData
     * @throws ProcessingException 如果无法读取数据
     */
    @Override
    public FrameData process(HandlerContext context) {
        Objects.requireNonNull(context);
        FrameInfo frameInfo = context.currentFrameInfo;

        // ===== 内存模式：数据已随帧保存在 FrameInfo 中 =====
        if (frameInfo.getCompressedBuffer() != null) {
            boolean isLocal = frameInfo.getLocalColorTable() != null;
            int[] colorTable = isLocal ? frameInfo.getLocalColorTable() : context.globalColorTable;
            return new FrameData(frameInfo.getCompressedBuffer(), colorTable, isLocal);
        }

        // ===== 文件映射模式：必须提供 mapperManager =====
        if (mapperManager == null) {
            throw new ProcessingException(
                    "无法读取帧数据：FileMappingManager 未注入，且帧内无内嵌数据");
        }

        int key = context.currentFrameIndex;
        return frameDataCache.fetchOrCompute(key, k -> {
            try {
                byte[] compressedBuffer = readFrameData(frameInfo, context.mappingContext);

                if (frameInfo.isLocalColorTable()) {
                    int[] colorTable = readLocalColorTable(frameInfo, context.mappingContext);
                    return new FrameData(compressedBuffer, colorTable, true);
                } else {
                    return new FrameData(compressedBuffer, context.globalColorTable, false);
                }
            } catch (IOException | DataFormatException e) {
                throw new ProcessingException("帧[" + k + "]读取失败", e);
            }
        });
    }

    // ==================== 内部读取方法 ====================

    /**
     * 读取并就地解包子块压缩数据。
     * 临时缓冲区通过 {@link PoolUtils} 从对象池借用，使用后交由 DataUtils 就地解包，
     * 最终由缓存淘汰监听器负责归还。
     */
    private byte[] readFrameData(FrameInfo frameInfo, MappingContext mappingContext)
            throws IOException, DataFormatException {
        final long dataOffset = frameInfo.getDataOffset();
        final int dataSize   = frameInfo.getDataSize();

        byte[] buffer = PoolUtils.borrowCompressedDataBuffer(poolManager, dataSize);
        try {
            int read = mapperManager.readWithAutoRecovery(dataOffset, dataSize, buffer, 0, mappingContext);
            if (read == -1) {
                throw new IOException("读取压缩数据失败，偏移=" + dataOffset + " 大小=" + dataSize);
            }
            return SubBlockUtils.unpackSubBlocks(buffer, 0, dataSize);
        } catch (Exception e) {
            PoolUtils.returnCompressedDataBuffer(poolManager, buffer);
            throw e;
        }
    }

    /**
     * 读取局部颜色表。
     * 临时字节缓冲区通过 {@link PoolUtils} 从对象池借用，读取后立即归还；
     * 颜色表 int 数组由 ColorUtils 从池中获取并直接返回，该数组为帧独享，
     * 后续由缓存淘汰监听器根据 isLocalColorTable 标志归还。
     */
    private int[] readLocalColorTable(FrameInfo frameInfo, MappingContext mappingContext)
            throws IOException {
        final long colorTableOffset = frameInfo.getLocalColorTableOffset();
        final int entryCount = frameInfo.getLocalColorTableSize();
        final int byteCount = entryCount * 3;                         // 实际需要的字节数

        byte[] colorBuf = PoolUtils.borrowColorTableBytes(poolManager, byteCount); // 按需借
        try {
            int read = mapperManager.readWithAutoRecovery(
                    colorTableOffset, byteCount, colorBuf, 0, mappingContext);
            if (read == -1) {
                throw new IOException("读取颜色表失败，偏移=" + colorTableOffset);
            }
            return ColorUtils.parseColorTable(colorBuf, 0, entryCount, poolManager);
        } finally {
            PoolUtils.returnColorTableBytes(poolManager, colorBuf);
        }
    }
}