package org.wsp.zen.mapping.core;

import java.io.IOException;

import org.wsp.zen.mapping.model.CompositeReadOperation;
import org.wsp.zen.mapping.model.MappingContext;
import org.wsp.zen.mapping.model.Segment;
import org.wsp.zen.mapping.model.WindowChange;

/**
 * 窗口片段管理器接口，定义窗口片段的管理、更新、查询、清除等核心行为。
 * <p>
 * 该接口负责根据映射请求（{@link MappingContext}）动态计算和管理文件内存映射窗口的片段集合。
 * 它支持：
 * <ul>
 *   <li>根据新的映射请求更新窗口片段，并返回需要添加和保留的片段变化（{@link #updateAndGetChanges(MappingContext)}）。</li>
 *   <li>根据读取起始偏移和长度，返回一个复合读取操作（{@link CompositeReadOperation}），该操作封装了从哪些窗口片段中读取数据以及数据在结果数组中的位置。</li>
 *   <li>查询当前片段数量、是否为空。</li>
 *   <li>移除或清空所有片段。</li>
 *   <li>释放资源（{@link #close()}）。</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全性：</b>
 * 实现类应确保方法调用是线程安全的。通常，写操作（如 {@link #updateAndGetChanges(MappingContext)}、
 * {@link #removeSegment(Segment)}、{@link #clear()}、{@link #close()}）需要互斥，
 * 而读操作（如 {@link #get(long, int)}、{@link #count()}、{@link #isEmpty()}）可以并发执行。
 * </p>
 *
 * <p><b>典型用法：</b>
 * <pre>{@code
 * SegmentManager segmentManager = ...;
 * MappingContext request = new MappingContext.Builder()
 *         .withMappingDirection(MappingDirection.FORWARD)
 *         .withWindowBaseOffset(0)
 *         .withWindowSize(1024 * 1024)
 *         .withAvailableFileSize(fileSize)
 *         .build();
 *
 * WindowChange changes = segmentManager.updateAndGetChanges(request);
 * // 应用变化（例如映射新增片段，释放旧片段）
 *
 * CompositeReadOperation op = segmentManager.get(1000, 500);
 * if (op != null) {
 *     // 使用 op 中的信息执行复合读取
 * }
 * }</pre>
 *
 * @author wsp
 * @version 1.0
 * @see MappingContext
 * @see Segment
 * @see CompositeReadOperation
 * @see WindowChange
 */
public interface SegmentManager extends AutoCloseable {

    /**
     * 根据新的映射请求更新当前管理的窗口片段，并返回本次更新所需的变化信息。
     * <p>
     * 该方法会基于请求中的映射方向、基准偏移量、窗口大小以及文件总大小，
     * 计算出新的理想窗口片段集合，并与当前已有的片段集合进行比较，
     * 得出需要新增的片段（{@link WindowChange#segmentsToAdd}）和需要保留的片段
     * （{@link WindowChange#segmentsToRetain}）。调用方可以根据变化信息执行实际的映射操作。
     * </p>
     *
     * @param mappingContext 映射请求，包含方向、基准偏移量、窗口大小、文件大小等信息，不能为 {@code null}
     * @return 窗口变更对象，包含需要添加的片段和需要保留的片段，不为 {@code null}
     * @throws NullPointerException     如果 {@code mappingContext} 为 {@code null}
     * @throws IllegalArgumentException 如果请求参数非法（如基准偏移量超出文件大小）
     */
    WindowChange updateAndGetChanges(MappingContext mappingContext);

    /**
     * 根据起始偏移量和读取长度，查询能够完整包含该范围的一个复合读取操作。
     * <p>
     * 该方法会查找当前管理的窗口片段集合，找到覆盖指定文件范围（从 {@code startOffset} 到
     * {@code startOffset + length - 1}）的片段，并构建一个 {@link CompositeReadOperation} 对象，
     * 其中包含从这些片段中读取数据的详细信息（每个片段的相对偏移量、读取长度以及在结果数组中的目标偏移量）。
     * 如果当前管理的片段集合无法完整覆盖该范围，则返回 {@code null}。
     * </p>
     *
     * @param startOffset 文件读取起始偏移量（≥ 0）
     * @param length      要读取的字节数（> 0）
     * @return 复合读取操作对象，如果当前片段无法覆盖请求范围则返回 {@code null}
     * @throws IllegalArgumentException 如果起始偏移量为负数或长度 ≤ 0
     */
    CompositeReadOperation get(long startOffset, int length);

    /**
     * 获取当前管理的窗口片段总数。
     *
     * @return 片段数量（≥ 0）
     */
    int count();

    /**
     * 判断当前管理的窗口片段集合是否为空。
     *
     * @return {@code true} 如果没有管理任何片段，否则 {@code false}
     */
    boolean isEmpty();

    /**
     * 从当前管理的片段集合中移除指定的窗口片段。
     *
     * @param segment 要移除的窗口片段，不能为 {@code null}
     * @return {@code true} 如果片段存在并被移除，否则 {@code false}
     * @throws NullPointerException 如果 {@code segment} 为 {@code null}
     */
    boolean removeSegment(Segment segment);

    /**
     * 清空当前管理的所有窗口片段，释放相关引用。
     * <p>
     * 调用后，{@link #count()} 将返回 0，{@link #isEmpty()} 返回 {@code true}。
     * </p>
     */
    void clear();

    /**
     * 关闭窗口片段管理器，释放所有内部资源。
     * <p>
     * 关闭后，除了再次调用 {@code close()} 方法外，任何其他方法调用的行为均为未定义。
     * 实现类可以选择在关闭后的非法调用中抛出 {@link IllegalStateException}。
     * 重复调用 {@code close()} 应无副作用。
     * </p>
     *
     * @throws IOException 如果关闭过程中发生 I/O 错误（具体子类型由实现决定）
     */
    @Override
    void close() throws IOException;
}