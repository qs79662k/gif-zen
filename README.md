Gif Zen — 高性能、纯 Java GIF 解码与渲染库

License: Apache 2.0

零第三方依赖，纯 Java 实现。一个让 Android 和桌面 JVM 都能丝滑播放损坏 GIF、在 12 MB 堆内存下不 OOM 的 GIF 解码库。

Glide 播不了损坏的 GIF？这个库能播。大图吃内存？三级缓存 + 对象池 + 文件映射把 GC 压到几乎为零。

读取 → 解码 → 渲染 三级流水线，解耦设计，各阶段可独立工作。
测试文件：640×370 GIF，35 帧循环，单帧压缩数据 80–90 KB。
冷帧处理约 2.5 ms，缓存命中 < 0.01 ms；12 MB 堆流畅顺序播放，16 MB 堆随机跳播无 OOM。


核心特性

  1. 智能容错，无惧损坏

     - GCE 损坏不绿屏：图形控制扩展损坏时，透明索引自动设为 -1，所有颜色正常绘制，后续帧恢复透明处理。
     - 块级错误恢复：自动跳过损坏数据块，搜索下一个合法块头（0x21/0x2c）重新同步，不丢弃整个文件。
     - LZW 解码异常分类：字典越界、数据截断等异常被精确捕获，触发上层恢复流程。
     - 连续零字节保护：可配置容忍连续 0x00 字节数量，防止死循环。

  2. 三级流水线，基于缓存的稳定接力

     - 读取、解码、渲染三个阶段各自独立为无状态的 Handler，通过流水线调度层并行处理不同帧（例如帧 N 渲染时，帧 N+1 解码中，帧 N+2 预读取）。
     - 缓存是流水线的核心枢纽：解码器内部不创建缓存，而是要求上层注入三个已注册的缓存实例（FRAME_DATA_CACHE, DECODE_FRAME_CACHE, RENDER_FRAME_CACHE）。这一设计并非为了降低灵活性，而是因为：
         * 流水线数据依赖：读/解码的结果必须写入缓存，后续帧的合成也直接从缓存读取参考帧。若没有缓存，三级流水线将无法协作。
         * 对象池归还的重要通道：对象池的归还途径是分散的——DefaultParser 和 DefaultRenderer 等组件在完成像素、颜色表等临时数组的使用后都会主动归还。但缓存淘汰时的 RemovalListener    是最大、最集中的归还点，当一帧从缓存中被逐出时，监听器会一次性归还该帧持有的压缩数据缓冲区、像素索引、局部颜色表和渲染图像。这保证了在播放窗口移动时，不再使用的资源能被及时回收，是维持低内存与零 GC 抖动的核心机制。
         * 核心性能特性的载体：双向预取窗口、智能关键帧检测、零 GC 抖动等特性全部依赖缓存窗口的驱逐策略与命中判断。
     - 因此，CacheManager 是强制性存在的构造参数，解码器实例独占其生命周期（加载新文件时清空缓存，关闭时清空并关闭缓存管理器）。
       若您希望最小化配置，只需在 Builder 中注册三个 DefaultCache 即可，无需任何自定义逻辑。
     - 智能关键帧：自动识别满画布不透明帧，跳帧播放时从最近关键帧解码，参考帧始终可获取。

  3. 流式解码，实时输出

     - 解析到第一帧即可通过回调通知上层，边解析边播放。
     - 支持加载过程中随意获取已解析的帧，实现流式加载与交互式预览。
     - minFrames 参数可平衡启动速度与流畅度。

  4. 三级缓存 + 对象池，接近零 GC

     - 帧原始数据缓存、解码帧缓存、渲染结果缓存。淘汰时通过 RemovalListener 自动归还对象池。同时渲染器、解析器等组件在完成阶段性工作后也会主动归还临时数组。
     - 局部/全局颜色表智能管理：局部颜色表可随帧缓存归还对象池，全局颜色表多帧共享不做归还。
     - 压缩数据缓冲区、颜色表、像素索引、行数组、渲染图像全部池化，典型播放全程无 new byte[] / new int[] 分配。

  5. 双源智能调度：文件映射 vs 内存持有

     - 文件映射模式（传入 String 路径）：SourceResolver 自动识别本地文件、HTTP(S) URL 或 Data URI，网络资源自动缓存至磁盘。解析器检测到流支持位置追踪时，只存储文件偏移量，压缩数据和局部颜色表均不占堆内存。后续通过 FileMappingManager 按需读取文件窗口片段，解码时临时借用对象池数组。
     - 内存流模式（直接传入 InputStream）：压缩数据和局部颜色表直接保存在 FrameInfo 中，适合小尺寸表情包，解码时零 IO 开销。

  6. 直接像素缓冲渲染，跨平台注入

     - 优先直接操作 int[] 像素数组，避免 Graphics2D 开销；Graphics2D 作为降级后备。
     - 渲染器通过 RendererFactory<T> 接口注入：桌面端 DefaultRenderer 输出 BufferedImage；Android 侧实现 Renderer<Bitmap> 即可。
     - 文件映射通过 FileMappingManagerFactory 接口注入：桌面端默认使用 NIO 实现；Android 可自定义基于 RandomAccessFile 的实现，完全避开 NIO 依赖。

  7. 循环播放与精确控制

     - 支持 NETSCAPE 循环扩展。
     - 帧延迟保证 ≥ 10 ms。
     - 提供总帧数、任意帧索引、帧延迟等查询。

  8. 全平台兼容

     +-----------+----------+----------------------------+
     | 平台       | 最低版本  | 映射/渲染实现                |
     +-----------+----------+----------------------------+
     | 桌面 JVM   | Java 9+  | NIO 内存映射，AWT 渲染       |
     | Android   | API 21+  | 自定义工厂注入，无 NIO/AWT    |
     +-----------+----------+----------------------------+

  核心代码仅依赖 InputStream 和 byte[]。文件映射与渲染通过 Builder 注入工厂接口实现解耦，桌面和移动端只需提供对应实现即可零成本移植。


架构概览

  ┌─────────────────────────────────────────────────────┐
  │                    外部输入                          │
  │          String (路径/URL/Data URI)                 │
  │          InputStream (内存流)                        │
  └───────────────────────┬─────────────────────────────┘
						  │
                          ▼
                 DefaultSourceResolver
          (识别本地文件/网络/Data URI，缓存策略)
                          │
                          ├─ 本地/缓存文件 → FileInputStream
                          ├─ 网络资源 → 下载到本地缓存 → FileInputStream
                          └─ Data URI → Base64解码 → 缓存文件
                          │
                          ▼
          PositionTrackingInputStream (可追踪位置)
                          │
                          ▼
                   DefaultParser
          (异步解析，单线程池 “GifParseThread”)
             │
             │ 持有 LzwDecompressor (用于关键帧检测)
             │ 持有 PoolManager (可选)
             │
             ├─ 解析 Header → 回调 onHeader
             ├─ 解析扩展块 → 回调 onGlobalExtensionParsed
             ├─ 解析帧 ImageDescriptor → 回调 onFrameParsed
             │      └─ 构建 FrameInfo (含偏移量/压缩数据等)
             │           └─ 若 InputStream 实现 ReadPositionAware → 文件映射模式
             │              (仅存偏移量，压缩数据/局部颜色表不占堆内存)
             │              否则 → 内存流模式 (数据存于 FrameInfo)
             └─ 解析完毕 → 回调 onParseComplete
                          │
                          ▼
               DefaultDecoder (主控)
     ┌─────────── 构建时注入 ───────────┐
     │  RendererFactory<T>            │→ 用于创建 Renderer<T>
     │  HandlerFactory<T>             │→ 创建三级Handler
     │  FileMappingManagerFactory     │→ 创建文件映射管理器
     │  CacheManager (必填, 含三个Cache)│
     │  PoolManager (可选)             │
     │  LzwDecompressorFactory        │
     │  SourceResolver                │
     │  线程池 (可外部提供)              │
     └────────────────────────────────┘
                          │
                          ▼
          加载完成: 持有 FrameInfo[] 列表
                   持有 ExtensionContainer (扩展数据)
                   持有 Header (画布尺寸, 全局颜色表等)
                   内部资源已初始化:
                     ├─ Renderer<T>         (由 RendererFactory 创建)
                     ├─ FileMappingManager  (文件模式时创建)
                     ├─ ReadHandler         (由 HandlerFactory 创建)
                     ├─ DecodeHandler       (由 HandlerFactory 创建)
                     └─ RenderHandler       (由 HandlerFactory 创建)
                          │
                          ▼
              用户调用 getFrame(index)
                          │
                          ▼
             renderCacheFramesInternal(…)
              计算缓存窗口范围，启动预取与渲染
                          │
              ┌───────────┴──────────────────┐
              ▼                              ▼
        processPath(主路径)      processPath(回绕路径, 异步)
              │                              │
              │  并行预取 (read/decode 提前)   │
              │  按序渲染当前帧                │
              │                              │
              └─────────── 每帧处理 ──────────┘
                          │
                          ▼
             renderHandler.process(context)
                  │
                  │ 内部调用链:
                  │  1. 从 renderFrameCache 查找已渲染帧
                  │  2. 未命中则:
                  │      a. 获取参考帧 (从 renderFrameCache)
                  │      b. 调用 decodeHandler.process(context)
                  │           │
                  │           │ 从 decodeFrameCache 查找已解码帧
                  │           │ 未命中则:
                  │           │   调用 readHandler.process(context)
                  │           │        │
                  │           │        │ 从 frameDataCache 查找原始帧数据
                  │           │        │ 未命中则:
                  │           │        │   [内存模式] 直接取 FrameInfo 内数据
                  │           │        │   [文件映射] FileMappingManager 读取
                  │           │        │       读取压缩数据 + 解包子块
                  │           │        │       读取局部颜色表(如有)
                  │           │   返回 FrameData (compressedBuffer, colorTable)
                  │           │   调用 LzwDecompressor.decodeFrame(...)
                  │           │   将压缩像素解码为 byte[] 像素索引
                  │           │        │
                  │           │   返回 DecodeFrame (pixelIndices, colorTable)
                  │           │
                  │      c. 构建 RenderContext (包含当前帧、参考帧等)
                  │      d. 调用 renderer.render(renderContext)
                  │           └─ 直接操作 int[] 像素缓冲 或 Graphics2D
                  │              输出 T (BufferedImage / Bitmap / ...)
                  │
                  ▼
           渲染结果 T 写入 renderFrameCache
           驱逐策略清理不再需要的缓存条目，
           缓存淘汰监听器自动归还对象池资源
                          │
                          ▼
                   返回给调用者 (CompletableFuture<T>)

  资源池与缓存交互 (持续运行)

   ┌──────────── CacheManager ───────────────────┐
   │  FRAME_DATA_CACHE   <Integer, FrameData>	 │
   │  DECODE_FRAME_CACHE <Integer, DecodeFrame>  │
   │  RENDER_FRAME_CACHE <Integer, T>            │
   └─────────────────────────────────────────────┘
          ▲                    ▲
          │ 写入/读取           │ 淘汰时触发 RemovalListener
          │                    │
      Handler 链       PoolManager (对象池)
                       ├─ byte[] 池 (压缩数据, 像素索引, 颜色表字节)
                       ├─ int[] 池 (字典数组, 行数组, 颜色表)
                       └─ T 池 (渲染图像, 由 renderImagePool 单独管理)
                            │
                            ▼
                      自动归还复用，减少 GC		

  快捷构建：桌面端可直接使用 BufferedImageDecoderBuilder.newBuilder() 一键获取全预设 Builder，无需手动配置对象池、缓存等组件，详见快速开始。


快速使用

    1. 添加 Maven 仓库

        <repositories>
            <repository>
                <id>github-gif-zen</id>
                <url>https://maven.pkg.github.com/qs79662k/gif-zen</url>
            </repository>
        </repositories>

    2. 添加依赖

        <dependency>
            <groupId>io.github.qs79662k</groupId>
            <artifactId>gif-zen</artifactId>
            <version>1.0.1</version>
        </dependency>

  桌面端示例（输出 BufferedImage）

    方式一：全预设快捷构建（推荐）

      使用 BufferedImageDecoderBuilder 一行构建，已内置默认的对象池和缓存配置，开箱即用。

      import org.wsp.zen.gif.impl.DefaultDecoder;
      import org.wsp.zen.gif.util.BufferedImageDecoderBuilder;

      import java.awt.image.BufferedImage;
      import java.io.File;
      import javax.imageio.ImageIO;

      public class Demo {
          public static void main(String[] args) throws Exception {
              // 全预设构建
              DefaultDecoder<BufferedImage> decoder = BufferedImageDecoderBuilder.newBuilder().build();

              // 加载 GIF
              decoder.load("https://example.com/anim.gif");
              // decoder.load("/path/to/local.gif");
              // decoder.load(inputStream);

              // 获取帧
              BufferedImage frame = decoder.getFrame(0).get();
              ImageIO.write(frame, "PNG", new File("frame0.png"));

              decoder.close();
          }
      }

    方式二：完全手动构建（适用于需要替换任意组件或自定义对象池的场景）

      该方式展示了如何从零配置一个解码器，是理解内部组件关系的完整示例。

      import org.wsp.zen.cache.core.CacheManager;
      import org.wsp.zen.cache.impl.DefaultCache;
      import org.wsp.zen.cache.impl.DefaultCacheManager;
      import org.wsp.zen.gif.impl.DefaultDecoder;
      import org.wsp.zen.gif.impl.DefaultRendererFactory;
      import org.wsp.zen.gif.model.CacheNames;
      import org.wsp.zen.gif.model.PoolNames;
      import org.wsp.zen.handler.impl.DefaultHandlerFactory;
      import org.wsp.zen.mapping.impl.DefaultFileMappingManagerFactory;
      import org.wsp.zen.pool.core.ObjectPool;
      import org.wsp.zen.pool.core.PoolManager;
      import org.wsp.zen.pool.impl.*;

      import java.awt.image.BufferedImage;
      import java.io.File;
      import javax.imageio.ImageIO;

      public class Demo {
          public static void main(String[] args) throws Exception {
              // 1. 构建缓存管理器（必填）
              CacheManager cacheManager = new DefaultCacheManager();
              cacheManager.register(CacheNames.FRAME_DATA_CACHE, new DefaultCache<>());
              cacheManager.register(CacheNames.DECODE_FRAME_CACHE, new DefaultCache<>());
              cacheManager.register(CacheNames.RENDER_FRAME_CACHE, new DefaultCache<>());

              // 2. 构建对象池管理器（可选，此处演示全部池化）
              PoolManager poolManager = new DefaultPoolManager();
              int cap = 2;
              poolManager.register(byte[].class, PoolNames.COMPRESSED_DATA,
                      new DefaultObjectPool<>(cap, new DefaultByteArrayFactory()));
              poolManager.register(byte[].class, PoolNames.PIXEL_INDICES,
                      new DefaultObjectPool<>(cap, new DefaultByteArrayFactory()));
              poolManager.register(byte[].class, PoolNames.COLOR_TABLE_BYTES,
                      new DefaultObjectPool<>(cap, new DefaultByteArrayFactory()));
              poolManager.register(int[].class, PoolNames.DICT_INT_ARRAY,
                      new DefaultObjectPool<>(cap, new DefaultIntArrayFactory()));
              poolManager.register(int[].class, PoolNames.ROW_INT_ARRAY,
                      new DefaultObjectPool<>(cap, new DefaultIntArrayFactory()));
              poolManager.register(int[].class, PoolNames.COLOR_TABLE_INT,
                      new DefaultObjectPool<>(cap, new DefaultIntArrayFactory()));
              poolManager.register(BufferedImage.class, PoolNames.RENDER_IMAGE,
                      new DefaultObjectPool<>(cap, new DefaultBufferedImageFactory()));

              // 3. 构建解码器
              DefaultDecoder<BufferedImage> decoder = new DefaultDecoder.Builder<BufferedImage>()
                      .withRendererFactory(new DefaultRendererFactory())
                      .withHandlerFactory(new DefaultHandlerFactory())
                      .withFileMappingManagerFactory(new DefaultFileMappingManagerFactory())
                      .withCacheManager(cacheManager)                     // 必填
                      .withPoolManager(poolManager)                       // 可选
                      .withRenderImageType(BufferedImage.class)           // 启用对象池时必填
                      .build();

              // 4. 使用 (与快捷构建一致)
              decoder.load("https://example.com/anim.gif");
              BufferedImage frame = decoder.getFrame(0).get();
              ImageIO.write(frame, "PNG", new File("frame0.png"));
              decoder.close();
          }
      }

  Android 端示例

    // 1. 缓存管理器（必填）
    CacheManager cacheManager = new DefaultCacheManager();
    cacheManager.register(CacheNames.FRAME_DATA_CACHE, new DefaultCache<>());
    cacheManager.register(CacheNames.DECODE_FRAME_CACHE, new DefaultCache<>());
    cacheManager.register(CacheNames.RENDER_FRAME_CACHE, new DefaultCache<>());

    // 2. 自定义 Bitmap 渲染器
    class AndroidRenderer implements Renderer<Bitmap> {
        private final int width, height;
        public AndroidRenderer(int w, int h) { this.width = w; this.height = h; }
        @Override
        public Bitmap render(RenderContext<Bitmap> context) {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            // 利用 context.decodeFrame.pixelIndices, colorTable 等填充 Bitmap
            return bitmap;
        }
    }

    // 3. 构建解码器（无需对象池也可流畅运行）
    DefaultDecoder<Bitmap> decoder = new DefaultDecoder.Builder<Bitmap>()
            .withRendererFactory((pm, w, h) -> new AndroidRenderer(w, h))
            .withHandlerFactory(new DefaultHandlerFactory())
            .withFileMappingManagerFactory(new AndroidMappingFactory())
            .withCacheManager(cacheManager)             // 必填
            .build();                                   // 未使用对象池，无需 withPoolManager/withRenderImageType

    decoder.load(getAssets().open("anim.gif"));
    Bitmap frame = decoder.getFrame(0);
    imageView.setImageBitmap(frame);


测试资源与 GUI 工具

  为了帮助开发者快速验证性能和容错能力，项目在 src/test 下提供了可直接运行的测试类及配套 GIF 文件。

  测试文件位置

    +-----------------------+----------------------------------------+----------------------------------------------------------------+
    | 文件                   | 路径                                    | 用途                                                			  |
    +-----------------------+----------------------------------------+----------------------------------------------------------------+
    | 正常测试 GIF           | src/test/resources/sample.gif          | 常规解析、性能基准测试、播放演示                           			  |
    | 损坏测试 GIF           | src/test/resources/corrupt.gif         | 验证容错恢复：损坏的 GCE、LZW 数据异常或截断等情况          			  |
	| 长帧测试 GIF 		    | src/test/resources/long.gif	         | 低分辨率长帧文件，可在低堆内存（如 -Xmx10m）下验证内存效率与解码稳定性   |
    | GUI 测试类             | src/test/java/.../GifTestViewer.java   | 图形界面播放器，实时显示每帧耗时                         			  |
    +-----------------------+----------------------------------------+----------------------------------------------------------------+

	运行 GUI 测试程序，请按以下步骤操作：

	1. 配置并启动测试程序  
		在 IDE 中打开 GifTestViewer.java 并执行 main 方法。  
		如需验证长帧文件在低内存下的表现，可在运行配置中添加 VM 参数 -Xmx10m，将最大堆内存限制为 10 MB。long.gif 图像分辨率较低，降低内存上限能更明显地测试解码器的内存效率与稳定性。

	2. 窗口启动后的操作与验证  
		- 切换数据源：选择文件路径 (String)或输入流 (InputStream)模式。  
		- 加载正常文件：点击浏览选择 sample.gif，或直接输入网络 GIF 的 URL，观察流畅播放与毫秒级耗时。  
		- 加载损坏文件：同样方式打开 corrupt.gif，确认库不会崩溃、不出现绿屏，并能正确跳过损坏部分继续显示可用帧。  
		- 加载长帧文件：浏览并选择 long.gif（低分辨率，若已在第 1 步限制堆内存，此处可重点观察低内存下的解码稳定性与流畅度）。  
		- 控制播放：使用暂停/播放、逐帧步进、拖拽进度条，所有操作均应实时响应，无卡顿或异常。

	3. 测试资源说明  
		项目在 src/test/resources 下提供了测试用 GIF 文件：sample.gif（正常文件）、corrupt.gif（损坏文件）和 long.gif（长帧、低分辨率文件）。图形界面测试类 GifTestViewer.java 可直接运行，用于直观验证 GIF 解码的性能与容错能力。


性能测试

  测试环境

    +----------+---------------------------------------------+
    | 项目      | 规格                                        |
    +----------+---------------------------------------------+
    | CPU      | Intel Core i7-10700 @ 2.90 GHz（基频）       |
    | JVM      | OpenJDK 17，堆内存 16 MB                     |
    | 测试素材  | 640×370 GIF（同 sample.gif），35 帧循环，      |
    |          | 含透明帧与全画布覆盖帧                          |
    +----------+---------------------------------------------+

  耗时测量（仅统计冷帧，排除缓存命中）

    +---------------------+-------------+-------------+-------------+
    | 帧类型               | 解码耗时(ms) | 渲染耗时(ms)  | 总耗时(ms)   |
    +---------------------+-------------+-------------+-------------+
    | 常规帧（冷帧）         | ~1.6       | ~0.9         | ~2.5       |
    | 首帧 / 关键帧         | ~2.5        | ~1.5        | 3.5–4.5     |
    | 缓存命中 / 简单增量帧  | < 0.01      | < 0.01      | 0.3–0.5     |
    +---------------------+-------------+-------------+-------------+

  顺序播放时，预取窗口使大部分帧命中缓存，视觉上零卡顿。
  理论吞吐量 > 300 FPS，远超 60 FPS 实时需求。

  极限堆内存测试

    +-----------+-----------------+---------------------------------------------------+
    | 最大堆     | 播放模式         | 结果                                               |
    +-----------+-----------------+---------------------------------------------------+
    | 10 MB     | 顺序播放         | 无 OOM，偶有 GC 毛刺（单帧 > 3 ms），整体稳定          |
    | 16 MB     | 顺序播放         | 零毛刺，所有帧 ≤ 3 ms                               |
    | 16 MB     | 随机跳播 / 倒播   | 无 OOM，正确返回任意帧                               |
    +-----------+-----------------+---------------------------------------------------+

  文件映射模式下，FrameInfo 仅约 56 字节/帧，一万帧仅占 ~560 KB，大帧数 GIF 亦可在极小堆下运行。


String 路径处理详解

  调用 decoder.load("https://...") 或 decoder.load("/local.gif") 时的内部流程：

  1. DefaultSourceResolver 识别源类型：本地文件直接打开带位置追踪的流；网络 URL 检查缓存，未缓存则下载并写入磁盘后打开流；Data URI 解码后缓存再打开流。所有返回的 InputStream 均实现了 ReadPositionAware。
  2. 解析器在解析图像描述符时检测 in instanceof ReadPositionAware：
       - 为真 → 文件映射模式，FrameInfo 中 compressedBuffer 和 localColorTable 均为 null，仅保留文件偏移量。
       - 为假 → 内存流模式，数据直接保存在 FrameInfo 内。
  3. 文件映射模式下，DefaultReadHandler 通过 FileMappingManager 按需读取窗口片段、解包子块，若有局部颜色表则一并读取；颜色表 int[] 从对象池获取，用后由缓存淘汰监听器归还。
  4. 内存流模式下，压缩数据和颜色表已驻留内存，ReadHandler 直接返回，无额外 IO。


容错机制

  - GCE 损坏 → 不绿屏：透明索引强制为 -1，所有颜色正常绘制，后续帧恢复透明。
  - LZW 数据异常：抛出 LzwCorruptedDataException，上层捕获后调用 recoverFromParsingError 跳过损坏块，寻找下一个合法块继续解析。
  - 文件映射异常恢复：读取时窗口片段缺失则自动清理脏片段、重映射窗口并重试，对上层透明。


可配置参数

  DefaultDecoder.Builder

    +---------------------------+-----------------+----------------------------------------------------------------------------------------------------------+
    | 参数                       | 默认值           | 说明                                                                                                     |
    +---------------------------+-----------------+----------------------------------------------------------------------------------------------------------+
    | cacheManager              | 必填             | 缓存管理器，必须包含三个已注册的缓存。解码器不内部创建。                                                           |
    | rendererFactory           | 需注入           | 跨平台渲染器实现，桌面端用 DefaultRendererFactory                                                            |
    | handlerFactory            | 需注入           | 处理器工厂，桌面端用 DefaultHandlerFactory                                                                  |
    | fileMappingManagerFactory | 需注入           | 文件映射工厂，桌面端用 DefaultFileMappingManagerFactory                                                     |
    | poolManager               | null            | 对象池管理器，若启用则必须同时提供 renderImageType                                                            |
    | renderImageType           | null            | 渲染结果的类型令牌，启用 poolManager 时必填                                                                  |
    | forwardWindowSize         | 2               | 正向预取帧数                                                                                              |
    | backwardWindowSize        | 4               | 反向预取帧数                                                                                              |
    | mappingWindowSize         | 5 MB            | 文件映射窗口大小                                                                                           |
    | minFrames                 | 1               | 至少解析到多少帧才认为加载完成                                                                               |
    | loadTimeout               | 5 秒            | 加载超时                                                                                                  |
    | sourceResolver            | 自动创建         | 可自定义源解析和缓存策略                                                                                     |
    +---------------------------+-----------------+----------------------------------------------------------------------------------------------------------+

  BufferedImageDecoderBuilder（快捷工具）

    BufferedImageDecoderBuilder.newBuilder() 返回一个已预设所有必需组件的 DefaultDecoder.Builder<BufferedImage>，具体包括：
      - 渲染器工厂：DefaultRendererFactory
      - 处理器工厂：DefaultHandlerFactory
      - 对象池管理器：注册了常用 byte[]/int[]/BufferedImage 池（池容量 2）
      - 缓存管理器：注册了三个必需缓存
      - 图像类型令牌：BufferedImage.class
    调用方仍可在此基础上通过 .withXxx() 方法覆盖任意配置。

  DefaultSourceResolver 缓存配置

    通过 DefaultSourceResolver.Builder 自定义：

    +------------------+------------------+-----------------------------+
    | 参数              | 默认值            | 说明                        |
    +------------------+------------------+-----------------------------+
    | cacheDirectory   | ~/.zen-cache     | 缓存目录路径                  |
    | connectTimeout   | 50000 ms         | HTTP 连接超时                |
    | readTimeout      | 100000 ms        | HTTP 读取超时                |
    | userAgent        | Chrome 120       | HTTP User-Agent             |
    | cacheFilePrefix /| zen_ / .cache    | 缓存文件名前后缀               |
    | suffix           |                  |                             |
    +------------------+------------------+-----------------------------+


跨平台可插拔接口

  +-----------------------------+-------------------------------------+-------------------------------------------+-----------------------------------------------+
  | 接口                         | 作用                                | 桌面端默认实现                               | Android 替代方案                               |
  +-----------------------------+-------------------------------------+-------------------------------------------+-----------------------------------------------+
  | FileMappingManagerFactory   | 创建文件内存映射                      | DefaultFileMappingManagerFactory（NIO）    | 自定义（RandomAccessFile，无 NIO）               |
  | RendererFactory<T>          | 创建渲染器                           | DefaultRendererFactory → BufferedImage     | 自定义 → Bitmap                                |
  | HandlerFactory              | 创建三级处理器                        | DefaultHandlerFactory                     | 可复用或自定义                                   |
  | LzwDecompressorFactory      | LZW 解压算法                         | DefaultLzwDecompressorFactory（纯 Java）   | 可直接复用                                      |
  | SourceResolver              | 源解析和缓存                          | DefaultSourceResolver（纯 java.io）        | 可直接复用                                      |
  +-----------------------------+-------------------------------------+-------------------------------------------+-----------------------------------------------+

  只需实现对应接口并通过 Builder 注入，核心解码逻辑无需任何修改。


协议

  本项目使用 Apache License 2.0。


致谢

  如果这个库对你有帮助，欢迎给它一个 Star。您的支持是我们持续完善的最大动力。如有意见或建议，欢迎提出。
