package org.wsp.zen.handler.impl;

import java.util.Objects;

import org.wsp.zen.cache.core.Cache;
import org.wsp.zen.gif.core.LzwDecompressor;
import org.wsp.zen.gif.exception.LzwCorruptedDataException;
import org.wsp.zen.gif.model.DecodeFrame;
import org.wsp.zen.gif.model.FrameData;
import org.wsp.zen.gif.model.FrameInfo;
import org.wsp.zen.handler.core.Handler;
import org.wsp.zen.handler.model.HandlerContext;

/**
 * 默认的解码处理器，对应 GIF 处理流水线的第二阶段。
 * <p>
 * 该处理器负责从读取阶段获取原始压缩帧数据，通过 LZW 解压得到像素索引数组，
 * 并将结果封装为 {@link DecodeFrame} 对象。解码结果会通过解码帧缓存进行复用，
 * 避免对同一帧的重复解压。
 * </p>
 *
 * <p><b>资源管理：</b>
 * 解码过程中会从 {@link FrameData} 中读取颜色表及其来源标识
 * ({@link FrameData#isLocalColorTable})，并将该标识传递至生成的
 * {@link DecodeFrame#isLocalColorTable} 字段。
 * 后续缓存淘汰监听器可根据此标识决定是否将颜色表归还给对象池：
 * <ul>
 *   <li>{@code true} — 局部颜色表（帧独享），可以安全归还。</li>
 *   <li>{@code false} — 全局颜色表（多帧共享），禁止归还以免影响其他帧。</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该处理器本身是无状态的（依赖的组件需各自保证线程安全），因此可在多线程环境中安全复用。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * Handler<FrameData> readHandler = ...;
 * LzwDecompressor decompressor = ...;
 * Cache<Integer, DecodeFrame> decodeCache = ...;
 *
 * Handler<DecodeFrame> decodeHandler = new DefaultDecodeHandler(
 *         decompressor, readHandler, decodeCache);
 *
 * HandlerContext context = ...;
 * DecodeFrame decoded = decodeHandler.process(context);
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see Handler
 * @see DecodeFrame
 * @see LzwDecompressor
 */
public class DefaultDecodeHandler implements Handler<DecodeFrame> {

    private final LzwDecompressor lzwDecompressor;
    private final Handler<FrameData> readHandler;
    private final Cache<Integer, DecodeFrame> decodeFrameCache;

    /**
     * 构造解码处理器。
     *
     * @param lzwDecompressor   LZW 解压器，用于解码压缩的像素数据，不能为 {@code null}
     * @param readHandler       前一阶段（读取处理器），用于获取原始帧数据，不能为 {@code null}
     * @param decodeFrameCache  解码结果缓存，用于复用已解码的帧，不能为 {@code null}
     * @throws NullPointerException 如果任一参数为 {@code null}
     */
    public DefaultDecodeHandler(
            LzwDecompressor lzwDecompressor,
            Handler<FrameData> readHandler,
            Cache<Integer, DecodeFrame> decodeFrameCache) {
        this.lzwDecompressor = Objects.requireNonNull(lzwDecompressor, "LZW 解码器不能为 null");
        this.readHandler = Objects.requireNonNull(readHandler, "帧数据读取器不能为 null");
        this.decodeFrameCache = Objects.requireNonNull(decodeFrameCache, "解码帧缓存不能为 null");
    }

    /**
     * 处理解码请求，返回指定帧的解码后数据。
     * <p>
     * 该方法首先尝试从 {@code decodeFrameCache} 获取已缓存的解码帧；
     * 若未命中，则通过 {@code readHandler} 获取原始压缩数据，调用 LZW 解压器进行解码，
     * 并将成功解码的结果存入缓存。解码成功时，会从 {@link FrameData} 中携带颜色表来源标志
     * ({@link FrameData#isLocalColorTable}) 到解码帧中。
     * 如果解码过程中发生 {@link LzwCorruptedDataException}，
     * 将返回一个表示失败的 {@link DecodeFrame} 对象（success 字段为 {@code false}），
     * 同时打印错误日志。
     * </p>
     *
     * @param context 处理上下文，包含当前帧索引和帧信息，不能为 {@code null}
     * @return 解码后的帧数据对象，如果解码失败则返回 success=false 的实例
     * @throws NullPointerException 如果 {@code context} 为 {@code null}
     */
    @Override
    public DecodeFrame process(HandlerContext context) {
        Objects.requireNonNull(context, "处理器上下文对象不能为 null");

        int key = context.currentFrameIndex;
        return decodeFrameCache.fetchOrCompute(key, k -> {
            FrameInfo frameInfo = context.currentFrameInfo;
            FrameData frameData = readHandler.process(context);

            try {
                byte[] decodedData = lzwDecompressor.decodeFrame(
                        frameData.compressedBuffer,
                        frameInfo.isInterlaced(),
                        frameInfo.getMinCodeSize(),
                        frameInfo.getWidth(),
                        frameInfo.getHeight());

                // 将颜色表及其来源标志一并传递到解码帧
                return new DecodeFrame(decodedData, frameData.colorTable, frameData.isLocalColorTable);
            } catch (LzwCorruptedDataException e) {
                System.err.println("帧[" + key + "]解码失败：" + e.getMessage());
                // 解码失败时，颜色表来源已无意义，可传 false
                return new DecodeFrame(null, null, false);
            }
        });
    }
}