package org.wsp.zen.mapping.model;

import java.util.Objects;

import org.wsp.zen.mapping.core.MappingDirection;
import org.wsp.zen.mapping.util.MappingValidateUtils;

/**
 * 文件内存映射请求参数，用于描述需要建立的内存映射窗口的规格。
 * <p>
 * 该对象包含映射方向、窗口基准偏移量、窗口大小以及可用文件大小等信息。
 * 它采用 Builder 模式构建，并在构造时进行参数合法性校验。
 * </p>
 *
 * <p><b>不可变性：</b>
 * 该类的实例是不可变的，所有字段在构造后不可更改，因此是线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * MappingContext request = new MappingContext.Builder()
 *         .withMappingDirection(MappingDirection.FORWARD)
 *         .withWindowBaseOffset(1024)
 *         .withWindowSize(4096)
 *         .withAvailableFileSize(102400)
 *         .build();
 *
 * // 后续可基于现有请求创建修改了文件大小的新请求
 * MappingContext updatedRequest = request.withAvailableFileSize(204800);
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see MappingDirection
 */
public final class MappingContext {

    /** 映射方向（正向/反向），决定窗口滑动的策略 */
    public final MappingDirection mappingDirection;

    /** 窗口基准偏移量（文件中的起始位置，单位字节），必须 ≥ 0 */
    public final long windowBaseOffset;

    /** 窗口大小（字节），-1 表示映射到整个文件剩余部分（从基准偏移到文件末尾） */
    public final int windowSize;

    /** 可用文件总大小（字节），-1 表示未知；若已知则用于校验窗口范围 */
    public final long availableFileSize;

    /**
     * 私有构造器，通过 Builder 创建实例，并在构造后执行校验。
     *
     * @param builder Builder 实例，不能为 {@code null}
     * @throws NullPointerException     如果 mappingDirection 为 null
     * @throws IllegalArgumentException 如果任意参数不符合约束
     */
    private MappingContext(Builder builder) {
        this.mappingDirection = builder.mappingDirection;
        this.windowBaseOffset = builder.windowBaseOffset;
        this.windowSize = builder.windowSize;
        this.availableFileSize = builder.availableFileSize;
        validate();
    }

    /**
     * 校验参数合法性：
     * <ul>
     *   <li>映射方向不能为 null</li>
     *   <li>基准偏移量 ≥ 0</li>
     *   <li>窗口大小允许 -1（整个文件），否则必须 > 0</li>
     *   <li>可用文件大小允许 -1（未知），否则必须 > 0</li>
     *   <li>如果文件大小已知，基准偏移量必须小于文件大小</li>
     * </ul>
     *
     * @throws NullPointerException     如果 mappingDirection 为 null
     * @throws IllegalArgumentException 如果任意参数不符合约束
     */
    private void validate() {
        Objects.requireNonNull(mappingDirection, "映射方向不能为 null");

        MappingValidateUtils.validateWindowBaseOffset(windowBaseOffset);

        if (windowSize != -1) {
            MappingValidateUtils.validateLength(windowSize);
        }

        if (availableFileSize != -1) {
            MappingValidateUtils.validateFileSize(availableFileSize);
        }

        if (availableFileSize != -1) {
            if (windowBaseOffset >= availableFileSize) {
                throw new IllegalArgumentException(
                        "窗口基准偏移量不能大于等于可用文件大小，偏移量：" + windowBaseOffset +
                        "，可用大小：" + availableFileSize);
            }
        }
    }

    /**
     * 基于当前请求，创建一个新的请求对象，仅修改可用文件大小。
     * <p>
     * 此方法用于在文件大小未知时先构建请求，待获取到实际文件大小后，
     * 生成一个包含正确大小的新请求。
     * </p>
     *
     * @param newAvailableFileSize 新的可用文件大小（-1 表示未知，否则必须 > 0）
     * @return 新的 MappingContext 实例，其他字段与当前请求相同
     * @throws IllegalArgumentException 如果 newAvailableFileSize 不合法
     */
    public MappingContext withAvailableFileSize(long newAvailableFileSize) {
        return new Builder()
                .withMappingDirection(this.mappingDirection)
                .withWindowBaseOffset(this.windowBaseOffset)
                .withWindowSize(this.windowSize)
                .withAvailableFileSize(newAvailableFileSize)
                .build();
    }

    /**
     * 构建器，用于创建 {@link MappingContext} 实例。
     * <p>
     * 所有字段均为可选，但构建时 {@link MappingDirection} 必须显式设置，
     * 否则会因 {@code null} 在校验阶段抛出异常。
     * </p>
     */
    public static class Builder {
        private MappingDirection mappingDirection;
        private long windowBaseOffset;
        private int windowSize = -1;
        private long availableFileSize = -1;

        /**
         * 设置映射方向。
         *
         * @param mappingDirection 映射方向，不能为 {@code null}
         * @return 当前 Builder 实例
         */
        public Builder withMappingDirection(MappingDirection mappingDirection) {
            this.mappingDirection = mappingDirection;
            return this;
        }

        /**
         * 设置窗口基准偏移量（文件起始位置）。
         *
         * @param windowBaseOffset 偏移量（字节），必须 ≥ 0
         * @return 当前 Builder 实例
         */
        public Builder withWindowBaseOffset(long windowBaseOffset) {
            this.windowBaseOffset = windowBaseOffset;
            return this;
        }

        /**
         * 设置窗口大小（字节）。
         *
         * @param windowSize 窗口大小，-1 表示从基准偏移到文件末尾；否则必须 > 0
         * @return 当前 Builder 实例
         */
        public Builder withWindowSize(int windowSize) {
            this.windowSize = windowSize;
            return this;
        }

        /**
         * 设置可用文件总大小（字节）。
         *
         * @param availableFileSize 文件大小，-1 表示未知；否则必须 > 0
         * @return 当前 Builder 实例
         */
        public Builder withAvailableFileSize(long availableFileSize) {
            this.availableFileSize = availableFileSize;
            return this;
        }

        /**
         * 构建 {@link MappingContext} 实例。
         *
         * @return 新的 MappingContext 对象
         * @throws NullPointerException     如果 mappingDirection 为 null（由校验抛出）
         * @throws IllegalArgumentException 如果参数组合非法（由校验抛出）
         */
        public MappingContext build() {
            return new MappingContext(this);
        }
    }
}