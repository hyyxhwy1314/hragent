package org.example.hragent.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.hragent.config.FeishuOpenApiProperties;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书消息卡片发送客户端（原生 OpenAPI，应用身份 tenant_access_token 鉴权）。
 * <p>
 * 覆盖「发送交互卡片」与「手机号/邮箱解析 open_id」两条链路，并按企业落地经验做了三点加固：
 * <ul>
 *   <li>令牌统一由 {@link FeishuTokenProvider} 缓存；遇到令牌失效/过期自动失效缓存并重试一次</li>
 *   <li>网络抖动、限流（99991400）自动重试一次，避免偶发失败导致审批人收不到待办</li>
 *   <li>open_id 解析结果本地缓存：命中缓存 30 分钟、未命中（负缓存）5 分钟，
 *       避免每次推送都调用通讯录接口</li>
 * </ul>
 * 推荐接收人标识类型为 {@code open_id}：邮箱未必是对方飞书账号绑定的邮箱，而 open_id 一定可送达。
 */
@Component
public class FeishuCardClient {

    private static final Logger log = LoggerFactory.getLogger(FeishuCardClient.class);

    /** 令牌失效/非法相关错误码，命中后刷新令牌并重试一次 */
    private static final Set<Integer> TOKEN_INVALID_CODES = Set.of(99991661, 99991663, 99991664, 99991668);

    /** 飞书限流错误码，命中后短暂等待并重试一次 */
    private static final int RATE_LIMIT_CODE = 99991400;

    /** 单次网络失败后的重试等待 */
    private static final long RETRY_DELAY_MS = 300L;

    /** open_id 命中缓存时长 */
    private static final long OPEN_ID_HIT_TTL_MS = 30 * 60_000L;

    /** open_id 未命中负缓存时长（避免占位手机号/邮箱反复打接口） */
    private static final long OPEN_ID_MISS_TTL_MS = 5 * 60_000L;

    private final FeishuOpenApiProperties props;
    private final FeishuTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, OpenIdCacheEntry> openIdCache = new ConcurrentHashMap<>();

    public FeishuCardClient(FeishuOpenApiProperties props,
                            FeishuTokenProvider tokenProvider,
                            ObjectMapper objectMapper) {
        this.props = props;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 发送交互卡片
     *
     * @param receiveId     接收人标识（依 receiveIdType 解释）
     * @param receiveIdType 接收人标识类型：open_id / email / user_id
     * @param cardJson      卡片 JSON 字符串
     * @return 是否发送成功
     */
    public boolean sendInteractiveCard(String receiveId, String receiveIdType, String cardJson) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", "interactive");
        body.put("content", cardJson);

        String url = props.getBaseUrl() + "/im/v1/messages?receive_id_type=" + receiveIdType;
        try {
            postWithToken(url, body);
            return true;
        } catch (FeishuApiException e) {
            log.warn("飞书卡片发送失败 receiveId={}, code={}, msg={}", receiveId, e.code(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("飞书卡片发送异常 receiveId={}, msg={}", receiveId, e.getMessage());
            return false;
        }
    }

    /**
     * 按手机号解析飞书用户 open_id（带本地缓存）
     *
     * @return 匹配到的 open_id；未匹配到返回 null
     */
    public String resolveOpenIdByMobile(String mobile) {
        return resolveOpenId("mobile:" + mobile, Map.of("mobiles", List.of(mobile)));
    }

    /**
     * 按邮箱解析飞书用户 open_id（带本地缓存）
     *
     * @return 匹配到的 open_id；未匹配到返回 null
     */
    public String resolveOpenIdByEmail(String email) {
        return resolveOpenId("email:" + email, Map.of("emails", List.of(email)));
    }

    /** 先查缓存（含负缓存），未命中再调用 batch_get_id 并写入缓存 */
    private String resolveOpenId(String cacheKey, Map<String, List<String>> identifiers) {
        OpenIdCacheEntry cached = cachedEntry(cacheKey);
        if (cached != null) {
            return cached.openId();
        }
        String openId = batchGetId(identifiers);
        cacheOpenId(cacheKey, openId);
        return openId;
    }

    /**
     * 读取缓存条目：返回 null 表示未命中；条目中的 openId 为 null 表示「已确认未匹配」的负缓存。
     */
    private OpenIdCacheEntry cachedEntry(String cacheKey) {
        OpenIdCacheEntry entry = openIdCache.get(cacheKey);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expireAt()) {
            openIdCache.remove(cacheKey);
            return null;
        }
        return entry;
    }

    private void cacheOpenId(String cacheKey, String openId) {
        long ttl = openId == null ? OPEN_ID_MISS_TTL_MS : OPEN_ID_HIT_TTL_MS;
        openIdCache.put(cacheKey, new OpenIdCacheEntry(openId, System.currentTimeMillis() + ttl));
    }

    /** 调用 batch_get_id，返回首个匹配用户的 open_id（无匹配返回 null） */
    private String batchGetId(Map<String, List<String>> identifiers) {
        String url = props.getBaseUrl() + "/contact/v3/users/batch_get_id?user_id_type=open_id";
        try {
            JsonNode node = postWithToken(url, identifiers);
            JsonNode userList = node.path("data").path("user_list");
            if (!userList.isArray() || userList.isEmpty()) {
                return null;
            }
            String openId = userList.get(0).path("user_id").asText("");
            return openId.isBlank() ? null : openId;
        } catch (FeishuApiException e) {
            log.warn("飞书用户 ID 解析失败 identifiers={}, code={}, msg={}{}",
                    identifiers, e.code(), e.getMessage(), permissionHint(e.code()));
            return null;
        } catch (Exception e) {
            log.warn("飞书用户 ID 解析异常 identifiers={}, msg={}", identifiers, e.getMessage());
            return null;
        }
    }

    /** 通讯录接口权限缺失时给出可执行的排查提示 */
    private String permissionHint(int code) {
        if (code == 99991672 || code == 99991679) {
            return "；请确认应用已开通「通过手机号或邮箱获取用户 ID」应用身份权限 contact:user.id:readonly";
        }
        return "";
    }

    /**
     * 携带令牌发起 POST 请求：
     * 网络异常 / 限流 / 令牌失效时各重试一次，其余业务错误直接抛出。
     */
    private JsonNode postWithToken(String url, Object body) {
        String jsonBody = json(body);
        boolean tokenRefreshed = false;
        boolean transientRetried = false;
        while (true) {
            String token = tokenProvider.token(props.getAppId(), props.getAppSecret(), props.getTokenEndpoint());
            JsonNode node;
            try {
                node = parse(postJson(url, jsonBody, Map.of("Authorization", "Bearer " + token)));
            } catch (IllegalStateException e) {
                if (!transientRetried) {
                    transientRetried = true;
                    sleepQuietly();
                    continue;
                }
                throw e;
            }
            int code = node == null ? -1 : node.path("code").asInt(-1);
            if (code == 0) {
                return node;
            }
            String msg = node == null ? "响应为空" : node.path("msg").asText("未知错误");
            if (!tokenRefreshed && TOKEN_INVALID_CODES.contains(code)) {
                tokenRefreshed = true;
                tokenProvider.invalidate(props.getAppId());
                continue;
            }
            if (!transientRetried && code == RATE_LIMIT_CODE) {
                transientRetried = true;
                sleepQuietly();
                continue;
            }
            throw new FeishuApiException(code, msg);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String postJson(String url, String jsonBody, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("飞书 API 网络请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("飞书 API 网络请求被中断", e);
        }
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (IOException e) {
            throw new IllegalStateException("飞书 API 响应解析失败: " + e.getMessage(), e);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("飞书 API 请求序列化失败", e);
        }
    }

    private record OpenIdCacheEntry(String openId, long expireAt) {
    }

    /** 飞书开放平台业务错误（code != 0），与网络异常区分以便决定是否重试 */
    private static final class FeishuApiException extends RuntimeException {
        private final int code;

        private FeishuApiException(int code, String message) {
            super(message);
            this.code = code;
        }

        private int code() {
            return code;
        }
    }
}
