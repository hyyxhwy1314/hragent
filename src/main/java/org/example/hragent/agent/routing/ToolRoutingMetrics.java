package org.example.hragent.agent.routing;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工具路由评估指标（轻量内存计数）。
 * <p>
 * 对应文章评估体系：
 * <ul>
 *   <li>召回层：候选集大小均值、Schema 估算 token 均值</li>
 *   <li>安全层：候选外工具拒绝数、角色越权拒绝数</li>
 *   <li>兜底层：低置信度次数、放宽召回次数</li>
 * </ul>
 */
@Component
public class ToolRoutingMetrics {

    private final AtomicLong routeCount = new AtomicLong();
    private final AtomicLong candidateTotal = new AtomicLong();
    private final AtomicLong schemaTokenTotal = new AtomicLong();
    private final AtomicLong lowConfidenceCount = new AtomicLong();
    private final AtomicLong recallWidenedCount = new AtomicLong();
    private final AtomicLong outOfCandidateRejected = new AtomicLong();
    private final AtomicLong roleDenied = new AtomicLong();

    /** 记录一次路由 */
    public void recordRoute(int candidateCount, int schemaTokens, boolean lowConfidence, boolean widened) {
        routeCount.incrementAndGet();
        candidateTotal.addAndGet(candidateCount);
        schemaTokenTotal.addAndGet(schemaTokens);
        if (lowConfidence) {
            lowConfidenceCount.incrementAndGet();
        }
        if (widened) {
            recallWidenedCount.incrementAndGet();
        }
    }

    /** 记录一次「模型调用候选外工具」被拒绝 */
    public void recordOutOfCandidateRejected() {
        outOfCandidateRejected.incrementAndGet();
    }

    /** 记录一次「角色越权」被拒绝 */
    public void recordRoleDenied() {
        roleDenied.incrementAndGet();
    }

    /** 指标快照 */
    public Map<String, Object> snapshot() {
        long routes = routeCount.get();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("routeCount", routes);
        snapshot.put("avgCandidates", routes == 0 ? 0.0 : (double) candidateTotal.get() / routes);
        snapshot.put("avgSchemaTokens", routes == 0 ? 0.0 : (double) schemaTokenTotal.get() / routes);
        snapshot.put("lowConfidenceCount", lowConfidenceCount.get());
        snapshot.put("recallWidenedCount", recallWidenedCount.get());
        snapshot.put("outOfCandidateRejected", outOfCandidateRejected.get());
        snapshot.put("roleDenied", roleDenied.get());
        return snapshot;
    }
}