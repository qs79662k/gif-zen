package org.wsp.zen.gif.impl;

import org.wsp.zen.gif.core.LzwDecompressor;
import org.wsp.zen.gif.core.LzwDecompressorFactory;
import org.wsp.zen.pool.core.PoolManager;

/**
 * {@link LzwDecompressorFactory} 的默认实现，用于创建 {@link DefaultLzwDecompressor} 实例。
 * <p>
 * 该工厂接收一个 {@link PoolManager}，并将其传递给解压器，解压器内部会按需从管理器中
 * 获取所需的对象池。这种无状态的设计使得工厂实例可被安全共享。
 * </p>
 *
 * @author wsp
 * @version 1.0
 * @see DefaultLzwDecompressor
 * @see PoolManager
 */
public class DefaultLzwDecompressorFactory implements LzwDecompressorFactory {

    /**
     * 创建一个 LZW 解压器实例。
     *
     * @param poolManager 对象池管理器，用于提供解码所需的缓冲区，不能为 {@code null}
     * @return 新创建的 {@link DefaultLzwDecompressor} 实例，不为 {@code null}
     * @throws NullPointerException 如果 {@code poolManager} 为 {@code null}
     */
    @Override
    public LzwDecompressor create(PoolManager poolManager) {
        return new DefaultLzwDecompressor(poolManager);
    }
}