package org.example.hragent.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书 tenant_access_token 统一提供者。
 * <p>
 * 卡片推送（{@code hragent.feishu}）与云文档 MCP（{@code hragent.mcp.feishu}）复用同一个自建应用，
 * 因此由本类集中缓存令牌，避免两个客户端各自维护一份缓存、各自刷新造成令牌漂移与多余请求。
 * 令牌按 appId 隔离缓存，过期前 5 分钟自动刷新；调用方收到「令牌无效」错误时可通过
 * {@link #invalidate(String)} 主动失效并重新获取。
 */
@Component
public class FeishuTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(FeishuTokenProvider.class);

    /** 提前刷新窗口：避免临界过期导致请求失败 */
    private static final long REFRESH_AHEAD_MS = 5 * 60_000L;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public FeishuTokenProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 获取应用身份令牌，未过期时直接复用缓存。
     *
     * @param appId         自建应用 App ID
     * @param appSecret     自建应用 App Secret
     * @param tokenEndpoint 获取 tenant_access_token 的端点
     */
    public String token(String appId, String appSecret, String tokenEndpoint) {
        CachedToken cached = cache.get(appId);
        if (cached != null && System.currentTimeMillis() < cached.expireAt()) {
            return cached.token();
        }
        return refresh(appId, appSecret, tokenEndpoint);
    }

    /** 主动失效缓存（收到令牌无效/过期错误时调用） */
    public void invalidate(String appId) {
        cache.remove(appId);
    }

    private synchronized String refresh(String appId, String appSecret, String tokenEndpoint) {
        // 双重检查：并发进入时可能已被其他线程刷新
        CachedToken cached = cache.get(appId);
        if (cached != null && System.currentTimeMillis() < cached.expireAt()) {
            return cached.token();
        }
        String raw = post(tokenEndpoint, write(Map.of("app_id", appId, "app_secret", appSecret)));
        JsonNode node = parse(raw);
        if (node == null || node.path("code").asInt(-1) != 0) {
            String msg = node == null ? "响应为空" : node.path("msg").asText("未知错误");
            throw new IllegalStateException("获取飞书 tenant_access_token 失败: " + msg);
        }
        String token = node.path("tenant_access_token").asText();
        if (token.isBlank()) {
            throw new IllegalStateException("获取飞书 tenant_access_token 失败: 响应未返回令牌");
        }
        long expireSeconds = node.path("expire").asLong(7200);
        cache.put(appId, new CachedToken(token,
                System.currentTimeMillis() + expireSeconds * 1000 - REFRESH_AHEAD_MS));
        log.debug("飞书 tenant_access_token 已刷新 appId={}, 有效期={}秒", appId, expireSeconds);
        return token;
    }

    private String post(String url, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        } catch (IOException e) {
            throw new IllegalStateException("飞书令牌接口网络请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("飞书令牌接口网络请求被中断", e);
        }
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (IOException e) {
            throw new IllegalStateException("飞书令牌响应解析失败: " + e.getMessage(), e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("飞书令牌请求序列化失败", e);
        }
    }

    private record CachedToken(String token, long expireAt) {
    }
}
