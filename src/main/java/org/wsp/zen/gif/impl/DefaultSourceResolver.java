package org.wsp.zen.gif.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

import org.wsp.zen.gif.core.SourceResolver;
import org.wsp.zen.io.impl.PositionTrackingInputStream;

/**
 * 跨平台的默认源解析器实现，完全基于 {@link File} 和传统 Java I/O，
 * 不依赖 {@code java.nio.file.Path}，可安全运行于桌面、Android、鸿蒙等所有 Java 平台。
 * <p>
 * 通过建造者模式提供全面可配置性：缓存目录、网络超时、请求头等。
 * 网络资源会自动缓存至本地，并利用缓存文件有效性检查避免重复下载。
 * </p>
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>本地文件：直接打开文件流，不缓存。</li>
 *   <li>网络 URL：先检查缓存有效性，若有效则直接使用缓存；否则下载并写入缓存。</li>
 *   <li>Data URI：解码 Base64 数据，同样缓存至本地文件供后续随机读取。</li>
 * </ul>
 *
 * <p><b>开箱即用：</b>
 * <pre>{@code
 * SourceResolver resolver = new DefaultSourceResolver();
 * }</pre>
 *
 * <b>深度定制：</b>
 * <pre>{@code
 * SourceResolver resolver = new DefaultSourceResolver.Builder()
 *     .cacheDirectory("/custom/cache")
 *     .connectTimeout(3000)
 *     .readTimeout(10000)
 *     .acceptHeader("image/*")
 *     .userAgent("MyApp/1.0")
 *     .build();
 * }</pre>
 * 所有可选项均有合理默认值。
 * </p>
 *
 * @author wsp
 * @version 3.1
 */
public class DefaultSourceResolver implements SourceResolver {

    // ==================== 模式常量 ====================
    private static final Pattern URL_PATTERN =
            Pattern.compile("^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");
    private static final Pattern DATA_URI_PATTERN =
            Pattern.compile("^data:image/gif;base64,", Pattern.CASE_INSENSITIVE);

    // ==================== 配置字段（全部基于 String/File） ====================
    private final File cacheDir;          // 缓存目录
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final String userAgent;
    private final String acceptHeader;
    private final String cacheFilePrefix;
    private final String cacheFileSuffix;

    /**
     * 使用所有默认设置创建解析器。
     * 缓存目录为 {@code ~/.zen-cache}。
     */
    public DefaultSourceResolver() {
        this(new Builder());
    }

    private DefaultSourceResolver(Builder builder) {
        this.cacheDir = new File(builder.cacheDir);
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.readTimeoutMs = builder.readTimeoutMs;
        this.userAgent = builder.userAgent;
        this.acceptHeader = builder.acceptHeader;
        this.cacheFilePrefix = builder.cacheFilePrefix;
        this.cacheFileSuffix = builder.cacheFileSuffix;

        // 确保缓存目录存在
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    // ==================== SourceResolver 接口实现 ====================

    @Override
    public SourceResult resolve(String source) throws IOException {
        Objects.requireNonNull(source, "source 不能为 null");

        if (DATA_URI_PATTERN.matcher(source).find()) {
            return resolveDataUri(source);
        }
        if (URL_PATTERN.matcher(source).matches()) {
            return resolveUrl(source);
        }
        return resolveLocalFile(source);
    }

    // -------------------- 本地文件 --------------------
    private SourceResult resolveLocalFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("文件不存在或非普通文件: " + filePath);
        }
        FileInputStream fis = new FileInputStream(file);
        InputStream trackingStream = new PositionTrackingInputStream(fis);
        return new SourceResult(trackingStream, file.getAbsolutePath());
    }

    // -------------------- 网络 URL --------------------
    private SourceResult resolveUrl(String urlString) throws IOException {
        String hash = computeHash(urlString);
        File cacheFile = new File(cacheDir, buildCacheFileName(hash, "url"));

        // 1. 缓存有效性检查（基于文件大小）
        if (isCacheFileValid(cacheFile)) {
            System.out.println("[DefaultSourceResolver] 使用完整缓存: " + cacheFile);
            FileInputStream fis = new FileInputStream(cacheFile);
            InputStream trackingStream = new PositionTrackingInputStream(fis);
            return new SourceResult(trackingStream, cacheFile.getAbsolutePath());
        }

        // 2. 下载并写入缓存
        System.out.println("[DefaultSourceResolver] 开始下载: " + urlString);
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Accept", acceptHeader);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            System.out.println("[DefaultSourceResolver] 响应码: " + responseCode);
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("下载失败，HTTP 状态码: " + responseCode);
            }

            // 将响应流写入缓存文件
            try (InputStream in = connection.getInputStream();
                 FileOutputStream fos = new FileOutputStream(cacheFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
            }

            // 打开缓存文件用于读取
            FileInputStream fis = new FileInputStream(cacheFile);
            InputStream trackingStream = new PositionTrackingInputStream(fis);
            return new SourceResult(trackingStream, cacheFile.getAbsolutePath());

        } catch (SocketTimeoutException e) {
            if (connection != null) connection.disconnect();
            throw new IOException("网络连接/读取超时", e);
        } catch (IOException e) {
            if (connection != null) connection.disconnect();
            // 下载失败时删除不完整的缓存文件
            cacheFile.delete();
            throw e;
        }
    }

    // -------------------- Data URI --------------------
    private SourceResult resolveDataUri(String dataUri) throws IOException {
        String hash = computeHash(dataUri);
        File cacheFile = new File(cacheDir, buildCacheFileName(hash, "data"));

        // 1. 缓存验证
        if (isCacheFileValid(cacheFile)) {
            System.out.println("[DefaultSourceResolver] 使用 Data URI 缓存: " + cacheFile);
            FileInputStream fis = new FileInputStream(cacheFile);
            InputStream trackingStream = new PositionTrackingInputStream(fis);
            return new SourceResult(trackingStream, cacheFile.getAbsolutePath());
        }

        // 2. 解码并缓存
        String base64Data = dataUri.substring(dataUri.indexOf(",") + 1);
        byte[] decoded = java.util.Base64.getDecoder().decode(base64Data);
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            fos.write(decoded);
        }

        FileInputStream fis = new FileInputStream(cacheFile);
        InputStream trackingStream = new PositionTrackingInputStream(fis);
        return new SourceResult(trackingStream, cacheFile.getAbsolutePath());
    }

    // -------------------- 缓存工具方法 --------------------
    private String buildCacheFileName(String hash, String type) {
        return cacheFilePrefix + type + "_" + hash + cacheFileSuffix;
    }

    private boolean isCacheFileValid(File cacheFile) {
        return cacheFile.exists() && cacheFile.isFile() && cacheFile.length() > 0;
    }

    private String computeHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    // ==================== 建造者 ====================
    /**
     * {@link DefaultSourceResolver} 的建造者，用于自定义所有可配置参数。
     * 未显式设置的项目均采用合理默认值。
     */
    public static class Builder {
        private String cacheDir = System.getProperty("user.home") + File.separator + ".zen-cache";
        private int connectTimeoutMs = 50_000;
        private int readTimeoutMs = 100_000;
        private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
        private String acceptHeader = "image/gif";
        private String cacheFilePrefix = "zen_";
        private String cacheFileSuffix = ".cache";

        /**
         * 设置缓存目录（平台无关的字符串路径）。
         * @param dir 缓存目录的绝对路径字符串
         * @return 当前建造者
         */
        public Builder cacheDirectory(String dir) {
            this.cacheDir = Objects.requireNonNull(dir, "缓存目录不能为 null");
            return this;
        }

        /**
         * 设置连接超时时间（毫秒）。
         * @param ms 超时毫秒数，必须大于 0
         * @return 当前建造者
         */
        public Builder connectTimeout(int ms) {
            if (ms <= 0) throw new IllegalArgumentException("连接超时必须大于 0");
            this.connectTimeoutMs = ms;
            return this;
        }

        /**
         * 设置读取超时时间（毫秒）。
         * @param ms 超时毫秒数，必须大于 0
         * @return 当前建造者
         */
        public Builder readTimeout(int ms) {
            if (ms <= 0) throw new IllegalArgumentException("读取超时必须大于 0");
            this.readTimeoutMs = ms;
            return this;
        }

        /**
         * 设置 HTTP User-Agent 请求头。
         * @param ua User-Agent 字符串
         * @return 当前建造者
         */
        public Builder userAgent(String ua) {
            this.userAgent = Objects.requireNonNull(ua, "User-Agent 不能为 null");
            return this;
        }

        /**
         * 设置 HTTP Accept 请求头，默认为 {@code "image/gif"}。
         * @param accept Accept 头字符串
         * @return 当前建造者
         */
        public Builder acceptHeader(String accept) {
            this.acceptHeader = Objects.requireNonNull(accept, "Accept 头不能为 null");
            return this;
        }

        /**
         * 设置缓存文件名的前缀，默认 {@code "zen_"}。
         * @param prefix 前缀字符串
         * @return 当前建造者
         */
        public Builder cacheFilePrefix(String prefix) {
            this.cacheFilePrefix = Objects.requireNonNull(prefix, "前缀不能为 null");
            return this;
        }

        /**
         * 设置缓存文件名的后缀，默认 {@code ".cache"}。
         * @param suffix 后缀字符串
         * @return 当前建造者
         */
        public Builder cacheFileSuffix(String suffix) {
            this.cacheFileSuffix = Objects.requireNonNull(suffix, "后缀不能为 null");
            return this;
        }

        /**
         * 构建 {@link DefaultSourceResolver} 实例。
         * @return 新的解析器实例
         */
        public DefaultSourceResolver build() {
            return new DefaultSourceResolver(this);
        }
    }
}