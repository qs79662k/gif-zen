package org.wsp.zen.gif.impl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

import org.wsp.zen.gif.core.Renderer;
import org.wsp.zen.gif.model.DecodeFrame;
import org.wsp.zen.gif.model.DisplayMode;
import org.wsp.zen.gif.model.FrameInfo;
import org.wsp.zen.gif.model.RenderContext;
import org.wsp.zen.gif.util.PoolUtils;
import org.wsp.zen.pool.core.PoolManager;

/**
 * GIF 渲染器的默认实现。
 * <p>
 * 该渲染器可通过 {@link PoolManager} 获取渲染帧对象池以复用 {@link BufferedImage} 画布，
 * 并通过像素数组的直接操作或 Graphics2D 后备方案完成背景恢复和前景绘制。
 * 内部使用的临时行数组也会优先通过 {@link PoolUtils} 从对象池借用。
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 该渲染器是无状态的（仅持有 {@link PoolManager} 引用和画布尺寸），
 * 因此多个线程可安全地并发调用 {@link #render(RenderContext)} 方法。
 * </p>
 *
 * <p><b>性能优化：</b>
 * 当画布与参考帧均为 {@link BufferedImage#TYPE_INT_ARGB} 或 {@link BufferedImage#TYPE_INT_RGB}
 * 类型时，渲染器会直接操作底层的 {@code int[]} 像素数组，避免 Graphics2D 的开销。
 * 临时行数组优先通过对象池（名称为 gif-row-int-array）复用。
 * </p>
 *
 * <p><b>降级策略：</b>
 * 对象池管理器（{@link PoolManager}）为可选项：
 * <ul>
 *   <li>若提供了对象池管理器，渲染器会复用图像和临时数组，减少内存分配。</li>
 *   <li>若未提供（{@code null}），或池中缺少相应资源，渲染器将直接创建新的 {@link BufferedImage} 和
 *       临时数组，功能完全不受影响，只是缺少内存优化。</li>
 * </ul>
 * 这种设计使得渲染器在无对象池的简单应用中同样可用。
 * </p>
 *
 * @author wsp
 * @version 1.1
 * @see Renderer
 * @see RenderContext
 */
public class DefaultRenderer implements Renderer<BufferedImage> {

    // ==================== 常量与字段 ====================

    private static final int BACKGROUND_COLOR = 0x00ffffff;

    /** 对象池管理器，可为 {@code null}（此时降级为直接新建对象） */
    private final PoolManager poolManager;

    private final int canvasWidth;
    private final int canvasHeight;

    // ==================== 构造器 ====================

    /**
     * 构造渲染器。
     *
     * @param poolManager  对象池管理器，允许为 {@code null}。
     * @param canvasWidth  画布宽度（像素），必须大于 0
     * @param canvasHeight 画布高度（像素），必须大于 0
     * @throws IllegalArgumentException 如果画布尺寸不合法
     */
    public DefaultRenderer(PoolManager poolManager, int canvasWidth, int canvasHeight) {
        this.poolManager = poolManager;
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            throw new IllegalArgumentException(
                "画布尺寸必须大于 0，当前宽度：" + canvasWidth + "，高度：" + canvasHeight);
        }
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
    }

    // ==================== 渲染入口 ====================

    @Override
    public BufferedImage render(RenderContext<BufferedImage> context) {
        BufferedImage canvas = PoolUtils.borrowRenderImage(poolManager, BufferedImage.class,
                canvasWidth, canvasHeight,
                (w, h) -> new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB));

        renderBackground(context, canvas);
        renderForeground(context, canvas);

        return canvas;
    }

    // ==================== 背景绘制 ====================

    /**
     * 绘制当前帧的背景，根据处置方法和参考帧恢复画布区域。
     */
    private void renderBackground(RenderContext<BufferedImage> context, BufferedImage canvas) {
        int cw = this.canvasWidth;
        int ch = this.canvasHeight;

        FrameInfo prevFrameInfo = context.prevFrameInfo;
        boolean hasPreviousFrame = prevFrameInfo != null;

        // 提取前一帧信息（若存在）
        DisplayMode prevDisplayMode = null;
        int prevOffsetX = 0, prevOffsetY = 0, prevWidth = 0, prevHeight = 0;
        if (hasPreviousFrame) {
            prevDisplayMode = prevFrameInfo.getDisplayMode();
            prevOffsetX = prevFrameInfo.getOffsetX();
            prevOffsetY = prevFrameInfo.getOffsetY();
            prevWidth = prevFrameInfo.getWidth();
            prevHeight = prevFrameInfo.getHeight();
        }

        BufferedImage referenceFrame = context.referenceFrame;

        // 不支持直接像素缓冲时，使用 Graphics2D 降级方案
        if (!usesIntPixelBuffer(canvas, referenceFrame)) {
            Graphics2D g = canvas.createGraphics();
            try {
                g.setColor(new Color(BACKGROUND_COLOR, true));
                if (referenceFrame == null) {
                    g.fillRect(0, 0, cw, ch);
                } else {
                    g.drawImage(referenceFrame, 0, 0, null);
                    if (hasPreviousFrame && prevDisplayMode == DisplayMode.RESTORE_BACKGROUND) {
                        g.fillRect(prevOffsetX, prevOffsetY, prevWidth, prevHeight);
                    }
                }
            } finally {
                g.dispose();
            }
            return;
        }

        // 直接像素操作
        int[] canvasPixels = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
        if (referenceFrame == null) {
            Arrays.fill(canvasPixels, BACKGROUND_COLOR);
            return;
        }

        // 需要恢复背景区域时，计算清除范围
        boolean needRestore = hasPreviousFrame && prevDisplayMode == DisplayMode.RESTORE_BACKGROUND;
        int clearX = 0, clearY = 0, clearMaxX = 0, clearMaxY = 0;
        boolean clearValid = false, fullCanvasClear = false;

        if (needRestore) {
            clearX = Math.max(0, prevOffsetX);
            clearY = Math.max(0, prevOffsetY);
            clearMaxX = Math.min(cw - 1, prevOffsetX + prevWidth - 1);
            clearMaxY = Math.min(ch - 1, prevOffsetY + prevHeight - 1);
            clearValid = (clearX <= clearMaxX && clearY <= clearMaxY);
            if (clearValid) {
                fullCanvasClear = clearX == 0 && clearY == 0 &&
                                 clearMaxX == cw - 1 && clearMaxY == ch - 1;
            }
        }

        if (fullCanvasClear) {
            Arrays.fill(canvasPixels, BACKGROUND_COLOR);
        } else {
            int[] referencePixels = ((DataBufferInt) referenceFrame.getRaster().getDataBuffer()).getData();
            int copyLen = Math.min(referencePixels.length, canvasPixels.length);
            System.arraycopy(referencePixels, 0, canvasPixels, 0, copyLen);

            // 如果需要恢复部分区域，使用对象池借用的行数组
            if (needRestore && clearValid) {
                int rowLen = clearMaxX - clearX + 1;
                int[] row = PoolUtils.borrowRowIntArray(poolManager, rowLen);
                Arrays.fill(row, 0, rowLen, BACKGROUND_COLOR);
                try {
                    int canvasRowStart = clearY * cw;
                    for (int y = clearY; y <= clearMaxY; y++) {
                        System.arraycopy(row, 0, canvasPixels, canvasRowStart + clearX, rowLen);
                        canvasRowStart += cw;
                    }
                } finally {
                    PoolUtils.returnRowIntArray(poolManager, row);
                }
            }
        }
    }

    // ==================== 前景绘制 ====================

    /**
     * 将解码后的像素索引根据颜色表绘制到画布上。
     */
    private void renderForeground(RenderContext<BufferedImage> context, BufferedImage canvas) {
        DecodeFrame decodeFrame = context.decodeFrame;
        if (decodeFrame == null || !decodeFrame.success) {
            return;
        }

        byte[] pixelIndices = decodeFrame.pixelIndices;
        int[] colorTable = decodeFrame.colorTable;
        FrameInfo frameInfo = context.currentFrameInfo;
        int frameOffsetX = frameInfo.getOffsetX();
        int frameOffsetY = frameInfo.getOffsetY();
        int frameWidth = frameInfo.getWidth();
        int frameHeight = frameInfo.getHeight();
        int transparentIndex = frameInfo.getTransparentColorIndex();

        if (usesIntPixelBuffer(canvas)) {
            // 直接像素操作
            int[] canvasPixels = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
            fillForegroundPixels(canvasPixels, canvasWidth, canvasHeight,
                    frameOffsetX, frameOffsetY, frameWidth, frameHeight,
                    pixelIndices, colorTable, transparentIndex);
        } else {
            // Graphics2D 降级方案：绘制临时图像再合成
            BufferedImage tempImage = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
            int[] tempPixels = ((DataBufferInt) tempImage.getRaster().getDataBuffer()).getData();
            fillForegroundPixels(tempPixels, frameWidth, frameHeight,
                    0, 0, frameWidth, frameHeight,
                    pixelIndices, colorTable, transparentIndex);
            Graphics2D g = canvas.createGraphics();
            try {
                g.drawImage(tempImage, frameOffsetX, frameOffsetY, null);
            } finally {
                g.dispose();
            }
        }
    }

    // ==================== 静态辅助方法 ====================

    /**
     * 将像素索引按颜色表填充到目标像素数组中，跳过透明索引。
     */
    private static void fillForegroundPixels(int[] targetPixels, int targetWidth, int targetHeight,
                                              int frameOffsetX, int frameOffsetY,
                                              int frameWidth, int frameHeight,
                                              byte[] pixelIndices, int[] colorTable,
                                              int transparentIndex) {
        int drawX = Math.max(0, frameOffsetX);
        int drawY = Math.max(0, frameOffsetY);
        int drawMaxX = Math.min(targetWidth - 1, frameOffsetX + frameWidth - 1);
        int drawMaxY = Math.min(targetHeight - 1, frameOffsetY + frameHeight - 1);

        if (drawX <= drawMaxX && drawY <= drawMaxY) {
            int drawWidth = drawMaxX - drawX + 1;
            int drawHeight = drawMaxY - drawY + 1;
            int sourceRowStart = (drawY - frameOffsetY) * frameWidth + (drawX - frameOffsetX);
            int targetRowStart = drawY * targetWidth + drawX;

            for (int y = 0; y < drawHeight; y++) {
                int sourceIdx = sourceRowStart;
                int targetIdx = targetRowStart;
                for (int x = 0; x < drawWidth; x++) {
                    int pixelIndex = pixelIndices[sourceIdx + x] & 0xff;
                    if (pixelIndex != transparentIndex) {
                        targetPixels[targetIdx + x] = colorTable[pixelIndex];
                    }
                }
                sourceRowStart += frameWidth;
                targetRowStart += targetWidth;
            }
        }
    }

    /**
     * 检查图像是否支持直接 int 像素缓冲操作。
     */
    private static boolean usesIntPixelBuffer(BufferedImage image) {
        if (image == null) return false;
        int type = image.getType();
        return type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_RGB;
    }

    /**
     * 检查画布和参考帧是否都支持 int 像素缓冲，且类型一致。
     */
    private static boolean usesIntPixelBuffer(BufferedImage canvas, BufferedImage referenceFrame) {
        return usesIntPixelBuffer(canvas) &&
                (referenceFrame == null ||
                 (usesIntPixelBuffer(referenceFrame) && canvas.getType() == referenceFrame.getType()));
    }
}