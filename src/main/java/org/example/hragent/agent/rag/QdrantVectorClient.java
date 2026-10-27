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
import java.time.LocalDate;
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

    /**
     * 检索过滤条件。
     *
     * @param visibilities    允许命中的可见性标签（权限预过滤）
     * @param statuses        允许命中的文档状态（正常检索仅 ACTIVE）
     * @param enforceValidity 是否强制校验生效/失效/复审有效期（过期提示查询需关闭）
     * @param asOf            检索基准日期（有效期判断以该日期为准）
     * @param reviewGraceDays 复审到期后的宽限天数
     */
    public record RetrievalFilter(Collection<String> visibilities,
                                  Collection<String> statuses,
                                  boolean enforceValidity,
                                  LocalDate asOf,
                                  int reviewGraceDays) {
    }

    private String collection() {
        return props.getQdrant().getCollection();
    }

    private URI uri(String path) {
        return URI.create("http://" + props.getQdrant().getHost() + ":" + props.getQdrant().getPort() + path);
    }

    /** 集合不存在时创建（幂等），并确保过滤所需的 payload 索引存在 */
    public void ensureCollection() {
        if (!collectionExists()) {
            createCollection();
        }
        ensurePayloadIndexes();
    }

    public boolean collectionExists() {
        HttpResponse<String> resp = send(HttpRequest.newBuilder(uri("/collections/" + collection())).GET().build());
        return resp.statusCode() == 200;
    }

    /** 重建集合：删除旧集合后重新创建（维度变更或重建索引时使用） */
    public void recreateCollection() {
        send(HttpRequest.newBuilder(uri("/collections/" + collection())).DELETE().build());
        createCollection();
        ensurePayloadIndexes();
    }

    /**
     * 为过滤字段建立 payload 索引（幂等）。
     * <p>
     * 可见性/状态/文档编码为 keyword，有效期与复审日期归一为 epochDay 整数后用 range 过滤。
     */
    public void ensurePayloadIndexes() {
        createIndex("visibility", "keyword");
        createIndex("status", "keyword");
        createIndex("docCode", "keyword");
        createIndex("effectiveFromDay", "integer");
        createIndex("effectiveToDay", "integer");
        createIndex("reviewDueDay", "integer");
    }

    /** 按文档编码物理删除该文档的全部向量（文档下线/重建时清理） */
    public void deleteByDocCode(String docCode) {
        ObjectNode body = mapper.createObjectNode();
        ObjectNode filter = body.putObject("filter");
        ArrayNode must = filter.putArray("must");
        ObjectNode condition = must.addObject();
        condition.put("key", "docCode");
        condition.putObject("match").put("value", docCode);

        HttpRequest request = HttpRequest.newBuilder(uri("/collections/" + collection() + "/points/delete?wait=true"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = send(request);
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("删除 Qdrant 向量失败：" + resp.body());
        }
    }

    /**
     * 按文档编码更新 payload 中的状态（不重写整份 payload）。
     * <p>
     * 版本替换时把旧版本置 SUPERSEDED、时效巡检时把过期文档置 EXPIRED，
     * 保证向量库状态与 MySQL 一致，同时保留旧版本向量用于“已失效提示”与审计回溯。
     */
    public void updateStatusByDocCode(String docCode, String status) {
        ObjectNode body = mapper.createObjectNode();
        ObjectNode payload = body.putObject("payload");
        payload.put("status", status);

        ObjectNode filter = body.putObject("filter");
        ArrayNode must = filter.putArray("must");
        ObjectNode condition = must.addObject();
        condition.put("key", "docCode");
        condition.putObject("match").put("value", docCode);

        HttpRequest request = HttpRequest.newBuilder(uri("/collections/" + collection() + "/points/payload?wait=true"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = send(request);
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("更新 Qdrant payload 失败：" + resp.body());
        }
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
     * @param vector   查询向量
     * @param topK     返回条数
     * @param minScore 相似度阈值，低于该值不返回
     * @param filter   检索过滤条件（可见性、状态、有效期）
     */
    public List<VectorHit> search(float[] vector, int topK, double minScore, RetrievalFilter filter) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode vectorNode = body.putArray("vector");
        for (float v : vector) {
            vectorNode.add(v);
        }
        body.put("limit", topK);
        body.put("score_threshold", minScore);
        body.put("with_payload", true);
        body.set("filter", buildFilter(filter));

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

    /**
     * 构建组合过滤：可见性 + 状态 + 有效期。
     * <p>
     * 有效期以 epochDay 整数承载：生效日 &lt;= 检索日、失效日 &gt;= 检索日、
     * 复审日 + 宽限期 &gt;= 检索日。过期文档因此在检索层被硬过滤，而不是仅降权。
     */
    private ObjectNode buildFilter(RetrievalFilter filter) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode must = root.putArray("must");
        addAnyMatch(must, "visibility", filter.visibilities());
        addAnyMatch(must, "status", filter.statuses());
        if (filter.enforceValidity()) {
            long asOf = filter.asOf().toEpochDay();
            addRange(must, "effectiveFromDay", "lte", asOf);
            addRange(must, "effectiveToDay", "gte", asOf);
            addRange(must, "reviewDueDay", "gte", asOf - filter.reviewGraceDays());
        }
        return root;
    }

    /** 字段命中任一取值（权限/状态过滤） */
    private void addAnyMatch(ArrayNode must, String key, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        ObjectNode condition = must.addObject();
        condition.put("key", key);
        ArrayNode any = condition.putObject("match").putArray("any");
        values.forEach(any::add);
    }

    /** 数值范围过滤（有效期/复审日期） */
    private void addRange(ArrayNode must, String key, String op, long value) {
        ObjectNode condition = must.addObject();
        condition.put("key", key);
        condition.putObject("range").put(op, value);
    }

    /** 创建 payload 索引（已存在时 Qdrant 返回 200，幂等） */
    private void createIndex(String field, String schema) {
        ObjectNode body = mapper.createObjectNode();
        body.put("field_name", field);
        body.put("field_schema", schema);

        HttpRequest request = HttpRequest.newBuilder(uri("/collections/" + collection() + "/index?wait=true"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = send(request);
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("创建 Qdrant payload 索引失败：" + field + " - " + resp.body());
        }
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
