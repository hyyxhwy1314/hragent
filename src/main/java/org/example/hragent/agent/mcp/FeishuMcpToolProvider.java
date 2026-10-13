package org.example.hragent.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.example.hragent.config.FeishuMcpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 飞书 MCP 工具提供者：将飞书远程 MCP 的工具接入 LangChain4j 工具调用链路
 * <p>
 * <ul>
 *     <li>惰性初始化：首次获取工具规格时连接 MCP 服务（initialize + tools/list），
 *         失败自动降级为空工具集并间隔重试，不影响 HR Agent 正常使用本地工具</li>
 *     <li>把 MCP 工具定义（JSON Schema）转换为 LangChain4j {@link ToolSpecification}，
 *         供模型在「思考」节点动态发现与调用</li>
 *     <li>在「动作」节点按工具名路由到 MCP 执行（tools/call）</li>
 * </ul>
 */
@Component
public class FeishuMcpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(FeishuMcpToolProvider.class);

    /** 初始化失败后的重试间隔（毫秒），避免每次推理都重复发起失败的网络请求 */
    private static final long RETRY_INTERVAL_MS = 60_000;

    private final FeishuMcpClient client;
    private final FeishuMcpProperties props;
    private final ObjectMapper objectMapper;
    private final Object lock = new Object();

    private volatile boolean initialized;
    private volatile long nextAttemptAt;
    private volatile List<ToolSpecification> toolSpecifications = List.of();
    private volatile Set<String> toolNames = Set.of();

    public FeishuMcpToolProvider(FeishuMcpClient client, FeishuMcpProperties props, ObjectMapper objectMapper) {
        this.client = client;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * 全部飞书 MCP 工具规格（可能为空列表）
     */
    public List<ToolSpecification> toolSpecifications() {
        ensureInitialized();
        return toolSpecifications;
    }

    /**
     * 判断某工具名是否由飞书 MCP 提供
     */
    public boolean handles(String toolName) {
        ensureInitialized();
        return toolNames.contains(toolName);
    }

    /**
     * 执行飞书 MCP 工具调用
     *
     * @param request 模型下发的工具调用请求（arguments 为 JSON 字符串）
     * @return 工具执行结果文本
     */
    public String execute(ToolExecutionRequest request) {
        if (!handles(request.name())) {
            return "未找到飞书 MCP 工具：" + request.name();
        }
        try {
            return client.callTool(request.name(), parseArguments(request.arguments()));
        } catch (Exception e) {
            return "飞书工具调用失败：" + e.getMessage();
        }
    }

    /**
     * 惰性初始化：已加载或未配置/未启用时直接返回，失败后按间隔重试
     */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        if (!props.isEnabled() || !props.hasCredentials()) {
            // 未启用或未配置凭据：固定为空工具集，无需重试
            initialized = true;
            return;
        }
        if (System.currentTimeMillis() < nextAttemptAt) {
            return;
        }
        synchronized (lock) {
            if (initialized || System.currentTimeMillis() < nextAttemptAt) {
                return;
            }
            try {
                // 按飞书接入流程：第 1 步 initialize 验证凭证，第 2 步 tools/list 获取工具列表
                client.initialize();
                List<FeishuMcpClient.McpTool> tools = client.listTools();
                toolSpecifications = tools.stream().map(this::toSpecification).collect(Collectors.toList());
                toolNames = tools.stream().map(FeishuMcpClient.McpTool::name).collect(Collectors.toSet());
                initialized = true;
                log.info("已接入飞书 MCP，可用工具 {} 个: {}", toolNames.size(), toolNames);
            } catch (Exception e) {
                nextAttemptAt = System.currentTimeMillis() + RETRY_INTERVAL_MS;
                log.warn("飞书 MCP 初始化失败（{} 秒后重试），本次会话暂不提供飞书工具: {}",
                        RETRY_INTERVAL_MS / 1000, e.getMessage());
            }
        }
    }

    /**
     * MCP 工具定义 -> LangChain4j ToolSpecification
     */
    private ToolSpecification toSpecification(FeishuMcpClient.McpTool tool) {
        return ToolSpecification.builder()
                .name(tool.name())
                .description(tool.description())
                .parameters(toToolParameters(tool.inputSchema()))
                .build();
    }

    /**
     * JSON Schema（inputSchema）-> ToolParameters（嵌套 Map 形式，兼容 OpenAI/DeepSeek 工具格式）
     */
    private ToolParameters toToolParameters(JsonNode schema) {
        Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (schema != null && schema.isObject()) {
            JsonNode req = schema.get("required");
            if (req != null && req.isArray()) {
                req.forEach(r -> required.add(r.asText()));
            }
            JsonNode props = schema.get("properties");
            if (props != null && props.isObject()) {
                props.fields().forEachRemaining(e -> properties.put(e.getKey(), toPropertySchema(e.getValue())));
            }
        }
        return ToolParameters.builder()
                .type("object")
                .properties(properties)
                .required(required)
                .build();
    }

    /**
     * 递归转换单个属性节点为 JSON Schema 形式的 Map
     */
    private Map<String, Object> toPropertySchema(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        String type = node.path("type").asText("string");
        map.put("type", type);
        if (node.hasNonNull("description")) {
            map.put("description", node.get("description").asText());
        }
        JsonNode enums = node.get("enum");
        if (enums != null && enums.isArray()) {
            List<String> enumValues = new ArrayList<>();
            enums.forEach(v -> enumValues.add(v.asText()));
            map.put("enum", enumValues);
        }
        if (node.hasNonNull("default")) {
            map.put("default", node.get("default"));
        }
        if ("object".equals(type) && node.has("properties") && node.get("properties").isObject()) {
            Map<String, Object> nested = new LinkedHashMap<>();
            node.get("properties").fields().forEachRemaining(e -> nested.put(e.getKey(), toPropertySchema(e.getValue())));
            map.put("properties", nested);
            List<String> nestedRequired = new ArrayList<>();
            JsonNode req = node.get("required");
            if (req != null && req.isArray()) {
                req.forEach(r -> nestedRequired.add(r.asText()));
            }
            if (!nestedRequired.isEmpty()) {
                map.put("required", nestedRequired);
            }
        }
        if ("array".equals(type) && node.has("items")) {
            map.put("items", toPropertySchema(node.get("items")));
        }
        return map;
    }

    /**
     * 解析工具调用的 arguments（JSON 字符串 -> Map）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(arguments, Object.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        } catch (IOException e) {
            return Map.of();
        }
    }
}
