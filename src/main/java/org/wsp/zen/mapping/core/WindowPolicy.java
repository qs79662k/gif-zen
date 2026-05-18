package org.wsp.zen.mapping.core;

import java.util.List;

import org.wsp.zen.mapping.model.MappingContext;
import org.wsp.zen.mapping.model.Segment;

/**
 * 窗口计算策略接口，用于根据映射请求计算需要映射的窗口片段列表。
 * <p>
 * 该接口定义了如何根据映射请求（包含方向、基准偏移量、窗口大小、文件总大小等）
 * 将请求的窗口范围划分为一个或多个连续的窗口片段（{@link Segment}）。
 * 不同的实现可以采用不同的策略，例如：
 * <ul>
 *   <li>单一窗口：整个窗口作为一个片段。</li>
 *   <li>分片窗口：将大窗口拆分为多个固定大小的片段，以便于内存映射管理。</li>
 *   <li>对齐窗口：按页对齐或按块对齐片段边界。</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 实现类应保证线程安全，因为策略计算方法可能被多线程并发调用。
 * 通常，无状态的策略实现是天然线程安全的。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * WindowPolicy policy = new FixedSizeWindowPolicy(4096);
 * MappingContext request = new MappingContext.Builder()
 *         .withMappingDirection(MappingDirection.FORWARD)
 *         .withWindowBaseOffset(0)
 *         .withWindowSize(1024 * 1024)
 *         .withAvailableFileSize(fileSize)
 *         .build();
 *
 * List<Segment> fragments = policy.calculateWindowFragments(request);
 * for (Segment seg : fragments) {
 *     manager.map(seg);
 * }
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see MappingContext
 * @see Segment
 */
public interface WindowPolicy {

    /**
     * 根据映射请求计算需要映射的窗口片段列表。
     * <p>
     * 实现类应确保返回的片段列表按照它们在文件中的顺序排列（升序），
     * 并且片段之间不应重叠，但可以相邻。
     * </p>
     *
     * @param mappingContext 映射请求，包含方向、基准偏移量、窗口大小和文件大小等信息，不能为 {@code null}
     * @return 窗口片段列表，不能为 {@code null}；如果请求的窗口大小为 0 或超出文件范围，可返回空列表
     * @throws NullPointerException     如果 {@code mappingContext} 为 {@code null}
     * @throws IllegalArgumentException 如果请求参数非法（例如基准偏移量超出文件大小）
     */
    List<Segment> calculateWindowFragments(MappingContext mappingContext);
}