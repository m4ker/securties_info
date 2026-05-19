package com.securities.info.datasource;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 共享的 HTTP 客户端工具
 */
public class HttpClientHelper {

    private static final Logger log = LoggerFactory.getLogger(HttpClientHelper.class);

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private HttpClientHelper() {}

    /**
     * 执行 GET 请求，返回响应体字符串
     */
    public static String get(String url) throws IOException {
        return get(url, null);
    }

    /**
     * 执行 GET 请求，携带 User-Agent
     */
    public static String get(String url, String userAgent) throws IOException {
        return get(url, userAgent, null);
    }

    /**
     * 执行 GET 请求，支持自定义请求头
     */
    public static String get(String url, String userAgent, Map<String, String> extraHeaders) throws IOException {
        log.debug("GET {}", url);
        Request.Builder builder = new Request.Builder().url(url);
        builder.header("User-Agent", userAgent != null ? userAgent
                : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for URL: " + url);
            }
            if (response.body() == null) {
                throw new IOException("Empty response body for URL: " + url);
            }
            return response.body().string();
        }
    }

    public static OkHttpClient getClient() {
        return CLIENT;
    }
}
