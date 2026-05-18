package org.wsp.zen.mapping.util;

import java.util.Objects;

import org.wsp.zen.mapping.core.MappingDirection;

/**
 * 文件映射相关的参数校验工具类，提供对偏移量、长度、文件大小等参数的合法性检查。
 * <p>
 * 该类的方法均为静态方法，用于在文件映射、窗口管理、数据读取等操作前验证参数，
 * 确保操作的安全性和正确性。所有方法在参数非法时均抛出 {@link IllegalArgumentException}。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>
 * MappingValidateUtils.validateRangeArguments(startOffset, length, fileSize);
 * MappingValidateUtils.validateReadBuffer(startOffset, length, buffer, 0, fileSize);
 * </pre>
 *
 * @author wsp
 * @version 1.0
 */
public final class MappingValidateUtils {

    // 工具类，禁止实例化
    private MappingValidateUtils() {}

    // ==================== 单偏移量校验 ====================

    /**
     * 校验起始偏移量不能为负数。
     *
     * @param startOffset 起始偏移量
     * @throws IllegalArgumentException 如果 {@code startOffset < 0}
     */
    public static void validateStartOffset(long startOffset) {
        if (startOffset < 0) {
            throw new IllegalArgumentException("起始偏移不能为负数");
        }
    }

    /**
     * 校验结束偏移量不能为负数。
     *
     * @param endOffset 结束偏移量
     * @throws IllegalArgumentException 如果 {@code endOffset < 0}
     */
    public static void validateEndOffset(long endOffset) {
        if (endOffset < 0) {
            throw new IllegalArgumentException("结束偏移量不能为负数：" + endOffset);
        }
    }

    // ==================== 偏移量组合校验 ====================

    /**
     * 校验起始偏移量和结束偏移量的关系：两者均非负，且起始 ≤ 结束。
     *
     * @param startOffset 起始偏移量
     * @param endOffset   结束偏移量
     * @throws IllegalArgumentException 如果起始或结束为负数，或起始 > 结束
     */
    public static void validateStartOffsetAndEndOffset(long startOffset, long endOffset) {
        validateStartOffset(startOffset);
        validateEndOffset(endOffset);

        if (startOffset > endOffset) {
            throw new IllegalArgumentException(
                    "起始值不能大于结束值：startOffset=" + startOffset + "，endOffset=" + endOffset);
        }
    }

    /**
     * 校验起始偏移量和文件大小：起始偏移非负且小于文件大小。
     *
     * @param startOffset 起始偏移量
     * @param fileSize    文件大小（必须 > 0）
     * @throws IllegalArgumentException 如果起始偏移为负数，或文件大小 ≤ 0，或起始偏移 ≥ 文件大小
     */
    public static void validateStartOffsetAndFileSize(long startOffset, long fileSize) {
        validateStartOffset(startOffset);
        validateFileSize(fileSize);

        if (startOffset >= fileSize) {
            throw new IllegalArgumentException(
                    "起始偏移量超出文件有效范围：0~" + (fileSize - 1) + "，实际值：" + startOffset);
        }
    }

    /**
     * 校验结束偏移量和文件大小：结束偏移非负且小于文件大小。
     *
     * @param endOffset 结束偏移量
     * @param fileSize  文件大小（必须 > 0）
     * @throws IllegalArgumentException 如果结束偏移为负数，或文件大小 ≤ 0，或结束偏移 ≥ 文件大小
     */
    public static void validateEndOffsetAndFileSize(long endOffset, long fileSize) {
        validateEndOffset(endOffset);
        validateFileSize(fileSize);

        if (endOffset >= fileSize) {
            throw new IllegalArgumentException(
                    "结束偏移量超出文件有效范围：文件大小为" + fileSize +
                    "，结束偏移量为" + endOffset +
                    "（有效范围应小于" + fileSize + "）");
        }
    }

    // ==================== 文件大小与长度校验 ====================

    /**
     * 校验文件大小必须大于 0。
     *
     * @param fileSize 文件大小
     * @throws IllegalArgumentException 如果 {@code fileSize <= 0}
     */
    public static void validateFileSize(long fileSize) {
        if (fileSize <= 0) {
            throw new IllegalArgumentException("空文件或无效文件：" + fileSize);
        }
    }

    /**
     * 校验长度必须大于 0。
     *
     * @param length 长度值
     * @throws IllegalArgumentException 如果 {@code length <= 0}
     */
    public static void validateLength(long length) {
        if (length <= 0) {
            throw new IllegalArgumentException("长度必须大于 0: " + length);
        }
    }

    /**
     * 校验长度必须大于 0 且不超过 {@link Integer#MAX_VALUE}。
     *
     * @param length 长度值
     * @throws IllegalArgumentException 如果长度 ≤ 0 或 > {@code Integer.MAX_VALUE}
     */
    public static void validateLengthWithinIntMax(long length) {
        validateLength(length);

        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "长度不能超过int最大值（" + Integer.MAX_VALUE + "），实际长度：" + length);
        }
    }

    // ==================== 窗口与范围校验 ====================

    /**
     * 校验窗口范围（起始到结束）长度不超过文件大小。
     *
     * @param startOffset 窗口起始偏移
     * @param endOffset   窗口结束偏移
     * @param fileSize    文件大小
     * @throws IllegalArgumentException 如果窗口长度 > 文件大小
     */
    public static void validateWindowRange(long startOffset, long endOffset, long fileSize) {
        if ((endOffset - startOffset + 1) > fileSize) {
            throw new IllegalArgumentException(
                    "窗口范围长度超过文件大小：窗口长度为 " +
                    (endOffset - startOffset + 1) +
                    "，文件大小为 " + fileSize);
        }
    }

    /**
     * 校验窗口基准偏移量不能为负数。
     *
     * @param windowBaseOffset 窗口基准偏移量
     * @throws IllegalArgumentException 如果 {@code windowBaseOffset < 0}
     */
    public static void validateWindowBaseOffset(long windowBaseOffset) {
        if (windowBaseOffset < 0) {
            throw new IllegalArgumentException("窗口基准偏移量不能为负数：" + windowBaseOffset);
        }
    }

    /**
     * 校验窗口基准偏移量：非负且小于文件大小。
     *
     * @param windowBaseOffset 窗口基准偏移量
     * @param fileSize         文件大小
     * @throws IllegalArgumentException 如果基准偏移量为负数，或 ≥ 文件大小
     */
    public static void validateWindowBaseOffset(long windowBaseOffset, long fileSize) {
        validateWindowBaseOffset(windowBaseOffset);

        if (windowBaseOffset >= fileSize) {
            throw new IllegalArgumentException(
                    "窗口基准偏移量不能大于等于文件大小，基准偏移量：" +
                    windowBaseOffset + "，文件大小：" + fileSize);
        }
    }

    // ==================== 特殊值校验 ====================

    /**
     * 校验参数值必须为 -1 或正数。
     *
     * @param value     参数值
     * @param fieldName 字段名称，用于错误消息
     * @throws IllegalArgumentException 如果值既不是 -1 也不是正数
     */
    public static void validatePositiveOrMinusOne(long value, String fieldName) {
        if (value != -1 && value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " 必须为 -1 或正数，实际值：" + value);
        }
    }

    // ==================== 重映射参数整体校验 ====================

    /**
     * 校验窗口重映射参数的整体合法性。
     *
     * @param mappingDirection 映射方向，不能为 null
     * @param windowBaseOffset 窗口基准偏移量（非负）
     * @param windowSize       窗口大小（可选，不在本方法校验）
     * @param fileSize         文件大小（> 0）
     * @throws NullPointerException     如果 {@code mappingDirection} 为 null
     * @throws IllegalArgumentException 如果文件大小 ≤ 0，或基准偏移量为负数，或基准偏移量 ≥ 文件大小
     */
    public static void validateRemapWindowParameters(
            MappingDirection mappingDirection,
            long windowBaseOffset, int windowSize, long fileSize) {
        Objects.requireNonNull(mappingDirection, "窗口映射方向不能为 null");
        validateFileSize(fileSize);

        if (windowBaseOffset < 0) {
            throw new IllegalArgumentException("窗口基准偏移量不能为负数：" + windowBaseOffset);
        }

        validateWindowBaseOffset(windowBaseOffset, fileSize);
    }

    // ==================== 读取范围参数校验 ====================

    /**
     * 校验读取范围参数（起始偏移和长度）。
     *
     * @param startOffset 起始偏移量（≥ 0）
     * @param length      读取长度（> 0）
     * @throws IllegalArgumentException 如果起始偏移为负数，或长度 ≤ 0
     */
    public static void validateRangeArguments(long startOffset, long length) {
        validateStartOffset(startOffset);
        validateLength(length);
    }

    /**
     * 校验读取范围参数（起始偏移和长度，长度使用 int 类型）。
     *
     * @param startOffset 起始偏移量（≥ 0）
     * @param length      读取长度（> 0）
     * @throws IllegalArgumentException 如果起始偏移为负数，或长度 ≤ 0
     */
    public static void validateRangeArguments(long startOffset, int length) {
        validateRangeArguments(startOffset, (long) length);
    }

    /**
     * 校验读取范围参数并确保不超出文件边界。
     *
     * @param startOffset 起始偏移量（≥ 0）
     * @param length      读取长度（> 0）
     * @param fileSize    文件大小（> 0）
     * @throws IllegalArgumentException 如果起始偏移为负数，长度 ≤ 0，或起始偏移 + 长度 > 文件大小
     */
    public static void validateRangeArguments(long startOffset, long length, long fileSize) {
        validateRangeArguments(startOffset, length);

        if (startOffset + length > fileSize) {
            throw new IllegalArgumentException(
                    "操作范围超出文件大小限制，起始值：" +
                    startOffset + "长度：" + length + "，文件大小：" + fileSize);
        }
    }

    /**
     * 校验读取范围参数（长度使用 int）并确保不超出文件边界。
     *
     * @param startOffset 起始偏移量（≥ 0）
     * @param length      读取长度（> 0）
     * @param fileSize    文件大小（> 0）
     * @throws IllegalArgumentException 如果参数非法
     */
    public static void validateRangeArguments(long startOffset, int length, long fileSize) {
        validateRangeArguments(startOffset, (long) length, fileSize);
    }

    // ==================== 目标数组与读取缓冲区校验 ====================

    /**
     * 校验目标数组中的起始偏移量不能为负数。
     *
     * @param targetOffset 目标数组偏移量
     * @throws IllegalArgumentException 如果 {@code targetOffset < 0}
     */
    public static void validateTargetOffset(int targetOffset) {
        if (targetOffset < 0) {
            throw new IllegalArgumentException("外部数组中的起始存放位置不能为负数，实际值：" + targetOffset);
        }
    }

    /**
     * 校验目标字节数组非空，且目标偏移量 + 长度不超出数组边界。
     *
     * @param b            目标字节数组
     * @param targetOffset 目标数组起始位置（≥ 0）
     * @param length       需要写入的长度（> 0）
     * @throws NullPointerException     如果 {@code b} 为 {@code null}
     * @throws IllegalArgumentException 如果 targetOffset 为负数，或 targetOffset + length > 数组长度
     */
    public static void validateByteArrayPosition(byte[] b, int targetOffset, int length) {
        Objects.requireNonNull(b, "目标字节数组不能为 null");
        validateTargetOffset(targetOffset);

        if (targetOffset + length > b.length) {
            throw new IllegalArgumentException(
                    "外部数组容量不足，需要存放位置+长度：" +
                    (targetOffset + length) + "，数组长度：" + b.length);
        }
    }

    /**
     * 校验读取操作的所有参数：起始偏移、长度、目标数组、目标偏移量、文件大小。
     *
     * @param startOffset  起始偏移量（≥ 0）
     * @param length       读取长度（> 0）
     * @param b            目标字节数组
     * @param targetOffset 目标数组起始位置（≥ 0）
     * @param fileSize     文件大小（> 0）
     * @throws NullPointerException     如果 {@code b} 为 {@code null}
     * @throws IllegalArgumentException 如果任何参数非法
     */
    public static void validateReadBuffer(long startOffset, int length, byte[] b, int targetOffset, long fileSize) {
        validateRangeArguments(startOffset, length, fileSize);
        validateByteArrayPosition(b, targetOffset, length);
    }
}