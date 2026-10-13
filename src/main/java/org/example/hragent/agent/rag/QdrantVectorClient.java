package org.example.hragent.agent.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量库轻量客户端（REST over HTTP）。
 * <p>
 * 仅覆盖知识库所需的最小能力：创建集合、写入向量、相似度检索。
 * 直接使用 JDK HttpClient + Jackson，避免引入额外的向量库 SDK 依赖。
 */
@Component
public class QdrantVectorClient {

    /** 单次写入批大小 */
    private static final int UPSERT_BATCH = 64;

    private final RagProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public QdrantVectorClient(RagProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** 单条待写入向量（id 使用 UUID 字符串，payload 承载引用所需的元数据） */
    public record VectorPoint(String id, List<Float> vector, Map<String, Object> payload) {
    }

    /** 单条检索命中结果 */
    public record VectorHit(String id, double score, Map<String, Object> payload) {
    }

    private String collection() {
        return props.getQdrant().getCollection();
    }

    private URI uri(String path) {
        return URI.create("http://" + props.getQdrant().getHost() + ":" + props.getQdrant().getPort() + path);
    }

    /** 集合不存在时创建（幂等） */
    public void ensureCollection() {
        if (collectionExists()) {
            return;
        }
        createCollection();
    }

    public boolean collectionExists() {
        HttpResponse<String> resp = send(HttpRequest.newBuilder(uri("/collections/" + collection())).GET().build());
        return resp.statusCode() == 200;
    }

    /** 重建集合：删除旧集合后重新创建（维度变更或重建索引时使用） */
    public void recreateCollection() {
        send(HttpRequest.newBuilder(uri("/collections/" + collection())).DELETE().build());
        createCollection();
    }

    /** 批量写入向量 */
    public void upsert(List<VectorPoint> points) {
        for (int i = 0; i < points.size(); i += UPSERT_BATCH) {
            upsertBatch(points.subList(i, Math.min(points.size(), i + UPSERT_BATCH)));
        }
    }

    /**
     * 相似度检索。
     *
     * @param vector       查询向量
     * @param topK         返回条数
     * @param minScore     相似度阈值，低于该值不返回
     * @param visibilities 允许命中的可见性标签（权限预过滤）
     */
    public List<VectorHit> search(float[] vector, int topK, double minScore, Collection<String> visibilities) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode vectorNode = body.putArray("vector");
        for (float v : vector) {
            vectorNode.add(v);
        }
        body.put("limit", topK);
        body.put("score_threshold", minScore);
        body.put("with_payload", true);
        body.set("filter", visibilityFilter(visibilities));

        HttpRequest request = HttpRequest.newBuilder(uri("/collections/" + collection() + "/points/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = send(request);
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("Qdrant 检索失败：" + resp.body());
        }
        return parseHits(resp.body());
    }

    private void createCollection() {
        ObjectNode body = mapper.createObjectNode();
        ObjectNode vectors = body.putObject("vectors");
        vectors.put("size", props.getQdrant().getVectorDim());
        vectors.put("distance", "Cosine");

        HttpRequest request = HttpRequest.newBuilder(uri("/collections/" + collection()))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = send(request);
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("创建 Qdrant 集合失败：" + resp.body());
        }
    }

    private void upsertBatch(List<VectorPoint> batch) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode points = body.putArray("points");
        for (VectorPoint point : batch) {
            ObjectNode node = points.addObject();
            node.put("id", point.id());
            ArrayNode vectorNode = node.putArray("vector");
            for (float v : point.vector()) {
                vectorNode.add(v);
            }
            node.set("payload", mapper.valueToTree(point.payload()));
        }

        HttpRequest request = HttpRequest.newBuilder(uri("/collections/" + collection() + "/points?wait=true"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = send(request);
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("写入 Qdrant 向量失败：" + resp.body());
        }
    }

    /** 可见性预过滤：仅命中调用方有权访问的片段 */
    private ObjectNode visibilityFilter(Collection<String> visibilities) {
        ObjectNode filter = mapper.createObjectNode();
        ArrayNode must = filter.putArray("must");
        ObjectNode condition = must.addObject();
        condition.put("key", "visibility");
        ArrayNode any = condition.putObject("match").putArray("any");
        visibilities.forEach(any::add);
        return filter;
    }

    private List<VectorHit> parseHits(String responseBody) {
        List<VectorHit> hits = new ArrayList<>();
        JsonNode root = readTree(responseBody);
        for (JsonNode node : root.path("result")) {
            hits.add(new VectorHit(
                    node.path("id").asText(),
                    node.path("score").asDouble(),
                    mapper.convertValue(node.path("payload"), new TypeReference<Map<String, Object>>() {
                    })));
        }
        return hits;
    }

    private JsonNode readTree(String responseBody) {
        try {
            return mapper.readTree(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("解析 Qdrant 响应失败：" + e.getMessage(), e);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Qdrant 请求失败：" + e.getMessage(), e);
        }
    }
}
