package org.example.hragent.agent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 精排（rerank）客户端：调用 qwen3.7-text-rerank。
 * <p>
 * 与主模型同一厂商、同一访问地址与密钥，仅模型名与接口路径不同：
 * 主机取自 {@code ai.base-url}（去掉兼容模式的 /compatible-mode/v1 后缀），
 * 密钥取自 {@code hragent.rag.rerank.api-key}（默认回落到 AI_API_KEY）。
 * 网络异常/非 200/响应非法时返回空列表，由检索层回退到向量粗排顺序。
 */
@Component
public class RerankClient {

    private static final Logger log = LoggerFactory.getLogger(RerankClient.class);

    private final RagProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Value("${ai.base-url:}")
    private String baseUrl;

    public RerankClient(RagProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /** 精排结果：文档下标与相关度得分（降序） */
    public record Ranked(int index, double score) {
    }

    /**
     * 对候选文档按与查询的相关度重新排序。
     *
     * @param query     用户问题
     * @param documents 候选文档（子块内容，含章节路径）
     * @param topN      返回条数上限
     * @return 相关度降序结果；精排关闭或失败时返回空列表
     */
    public List<Ranked> rerank(String query, List<String> documents, int topN) {
        RagProperties.Rerank config = props.getRerank();
        if (!config.isEnabled() || documents == null || documents.isEmpty()) {
            return List.of();
        }
        String apiKey = config.getApiKey() == null ? "" : config.getApiKey().trim();
        if (apiKey.isBlank()) {
            log.warn("未配置精排密钥（AI_API_KEY / AI_RERANK_API_KEY），本次跳过精排");
            return List.of();
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", config.getModelName());
            ObjectNode input = body.putObject("input");
            input.put("query", query);
            ArrayNode docs = input.putArray("documents");
            documents.forEach(docs::add);
            ObjectNode parameters = body.putObject("parameters");
            parameters.put("return_documents", false);
            parameters.put("top_n", Math.min(topN, documents.size()));

            HttpRequest request = HttpRequest.newBuilder(URI.create(host() + config.getPath()))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                log.warn("精排请求失败，降级为向量粗排：status={}, body={}", response.statusCode(), response.body());
                return List.of();
            }
            return parse(response.body());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("精排请求异常，降级为向量粗排：{}", e.getMessage());
            return List.of();
        }
    }

    private List<Ranked> parse(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        List<Ranked> ranked = new ArrayList<>();
        for (JsonNode node : root.path("output").path("results")) {
            ranked.add(new Ranked(node.path("index").asInt(-1), node.path("relevance_score").asDouble()));
        }
        ranked.sort((a, b) -> Double.compare(b.score(), a.score()));
        return ranked.stream().filter(item -> item.index() >= 0).toList();
    }

    /** 主模型地址去掉兼容模式路径后即为厂商主机地址 */
    private String host() {
        String url = baseUrl == null ? "" : baseUrl.trim();
        int compatibleMode = url.indexOf("/compatible-mode");
        String host = compatibleMode > 0 ? url.substring(0, compatibleMode) : url;
        return host.replaceAll("/+$", "");
    }
}
