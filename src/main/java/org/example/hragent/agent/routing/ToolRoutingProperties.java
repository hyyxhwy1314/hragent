package org.example.hragent.agent.routing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 分层 Tool Routing 配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hragent.agent.routing")
public class ToolRoutingProperties {

    /** 运行环境：dev / prod，用于生产环境静态黑名单 */
    private String environment = "dev";

    /** 单次召回返回的候选工具数上限 */
    private int topK = 10;

    /** 域收缩后候选不足时，放宽到全部可见工具并扩大返回的条数 */
    private int expandedTopK = 18;

    /** 候选数低于该值时触发放宽召回 */
    private int minCandidates = 3;

    /** 召回最高分低于该值视为低置信度（仅用于评估，不阻断执行） */
    private double lowConfidenceScore = 0.5;

    /** 常驻工具：高频低风险，任何请求都预绑定 */
    private List<String> pinnedTools = List.of(
            "queryEmployeeInfo", "queryEmployeeList", "queryProcessInstances",
            "queryMyTodoTasks", "hrKnowledgeQA");

    /** 全局禁用工具（任意环境都不暴露） */
    private List<String> blockedTools = List.of();

    /** 生产环境禁用工具（如真实高风险写操作） */
    private List<String> productionBlockedTools = List.of();

    /** 是否启用语义召回（预留，默认关闭，仅用 BM25） */
    private boolean embeddingEnabled = false;
}