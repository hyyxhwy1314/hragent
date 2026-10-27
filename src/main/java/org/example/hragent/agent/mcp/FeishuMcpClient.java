package org.example.hragent.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.hragent.config.FeishuMcpProperties;
import org.example.hragent.feishu.FeishuTokenProvider;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 飞书官方远程 MCP 客户端（JSON-RPC 2.0 over HTTP POST）
 * <p>
 * 对接 https://mcp.feishu.cn/mcp：
 * <ol>
 *     <li>通过 app_id/app_secret 换取 tenant_access_token（TAT），缓存并在过期前自动刷新</li>
 *     <li>{@code initialize} 验证凭证 → {@code tools/list} 获取可用工具 → {@code tools/call} 执行工具</li>
 *     <li>每个请求携带 {@code X-Lark-MCP-TAT} 与 {@code X-Lark-MCP-Allowed-Tools} 请求头</li>
 * </ol>
 * 响应兼容普通 JSON 与 Streamable HTTP 的 SSE 分块两种格式。
 */
@Component
public class FeishuMcpClient {

    private static final Logger log = LoggerFactory.getLogger(FeishuMcpClient.class);

    private final FeishuMcpProperties props;
    private final FeishuTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicLong requestId = new AtomicLong(0);

    public FeishuMcpClient(FeishuMcpProperties props,
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
     * 获取应用身份令牌（tenant_access_token），复用统一的令牌缓存
     */
    public String tenantAccessToken() {
        return tokenProvider.token(props.getAppId(), props.getAppSecret(), props.getTokenEndpoint());
    }

    /**
     * 初始化连接（验证凭证），返回服务器协议信息
     */
    public JsonNode initialize() {
        return invoke("initialize", null);
    }

    /**
     * 列出当前凭证与 allowed-tools 头允许调用的 MCP 工具
     *
     * @return 工具定义列表（name/description/inputSchema）
     */
    public List<McpTool> listTools() {
        JsonNode result = invoke("tools/list", null);
        List<McpTool> tools = new ArrayList<>();
        JsonNode arr = result.path("tools");
        if (arr.isArray()) {
            for (JsonNode t : arr) {
                tools.add(new McpTool(
                        t.path("name").asText(),
                        t.path("description").asText(""),
                        t.path("inputSchema")));
            }
        }
        return tools;
    }

    /**
     * 调用一个 MCP 工具
     *
     * @param name      工具名称（须在 allowed-tools 列表内）
     * @param arguments 工具入参
     * @return 工具执行结果文本；工具内部报错时返回错误文本而非抛出异常
     */
    public String callTool(String name, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", arguments == null ? Map.of() : arguments);
        JsonNode result = invoke("tools/call", params);
        String text = extractText(result.path("content"));
        if (result.path("isError").asBoolean(false)) {
            return "飞书工具执行失败：" + (text.isEmpty() ? "未知错误" : text);
        }
        return text;
    }

    /**
     * 发起 JSON-RPC 请求并返回 result 节点；顶层 error 时抛出异常
     */
    private JsonNode invoke(String method, Object params) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId.incrementAndGet());
        request.put("method", method);
        if (params != null) {
            request.put("params", params);
        }
        String raw = postJson(props.getEndpoint(), json(request),
                Map.of("X-Lark-MCP-TAT", tenantAccessToken(),
                        "X-Lark-MCP-Allowed-Tools", props.getAllowedTools()));
        JsonNode node = parseBody(raw);
        if (node == null) {
            throw new IllegalStateException("MCP 响应为空");
        }
        JsonNode error = node.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new IllegalStateException("MCP " + method + " 失败(" + error.path("code").asInt()
                    + "): " + error.path("message").asText());
        }
        return node.path("result");
    }

    /**
     * 发送 HTTP POST（application/json），返回响应体原始字符串
     */
    private String postJson(String url, String jsonBody, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("MCP 网络请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP 网络请求被中断", e);
        }
    }

    /**
     * 兼容普通 JSON 与 SSE（data: {...} 分块）两种响应体格式，返回最后一个 JSON 节点
     */
    private JsonNode parseBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            return readTree(trimmed);
        }
        // SSE 分块：取最后一个 data: 行
        JsonNode last = null;
        for (String line : raw.split("\\r?\\n")) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if (!data.isEmpty()) {
                    last = readTree(data);
                }
            }
        }
        return last;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("MCP 响应 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("MCP 请求 JSON 序列化失败", e);
        }
    }

    /**
     * 从 MCP content 数组中提取全部 text 文本
     */
    private String extractText(JsonNode content) {
        StringBuilder sb = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode item : content) {
                if ("text".equals(item.path("type").asText())) {
                    String text = item.path("text").asText();
                    if (!text.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(text);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * MCP 工具定义（name/description/inputSchema JSON Schema）
     */
    public record McpTool(String name, String description, JsonNode inputSchema) {
    }
}
