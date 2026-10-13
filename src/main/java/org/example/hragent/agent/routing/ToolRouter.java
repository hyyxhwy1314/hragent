package org.example.hragent.agent.routing;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 分层 Tool Routing 编排入口（对应「先确定性过滤 → 分层召回 → 模型精选 → 执行鉴权」）。
 * <p>
 * 【整条链路，自上而下】：
 * <ol>
 *   <li><b>注册</b>：{@link ToolRegistry} 聚合本地 @Tool 与远程 MCP 工具，产出可路由的 {@link ToolCard}
 *       （元数据来自注册表内置的声明式配置表）</li>
 *   <li><b>硬过滤</b>（本类 {@link #hardFilter}）：静态 Blocklist + 生产 Blocklist + 动态（角色可见性 / 运行时禁用）</li>
 *   <li><b>粗分类</b>：{@link ToolClassifier} 识别业务域，把候选收缩到域内</li>
 *   <li><b>召回</b>：{@link ToolRecallStrategy}（默认 {@link Bm25ToolRecallStrategy}）对域内候选打分取 Top-K，
 *       候选不足则放宽到全部可见工具</li>
 *   <li><b>精选与兜底</b>（本类 {@link #route}）：召回结果 + 常驻（pinned）工具；无任何候选时回退只读核心工具，
 *       产出 {@link Result} 绑定给模型</li>
 *   <li><b>执行前鉴权</b>（本类 {@link #isAllowed}）：工具存在、启用、未被拦截、角色可见，供执行节点二次校验</li>
 * </ol>
 * 第 1~5 层在本类完成；模块输入为 {@link Context}，输出为 {@link Result}。
 */
@Component
public class ToolRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolRouter.class);

    private final ToolRegistry registry;
    private final ToolClassifier classifier;
    private final ToolRecallStrategy recallStrategy;
    private final ToolRoutingProperties props;
    private final ToolRoutingMetrics metrics;

    public ToolRouter(ToolRegistry registry,
                      ToolClassifier classifier,
                      ToolRecallStrategy recallStrategy,
                      ToolRoutingProperties props,
                      ToolRoutingMetrics metrics) {
        this.registry = registry;
        this.classifier = classifier;
        this.recallStrategy = recallStrategy;
        this.props = props;
        this.metrics = metrics;
    }

    /**
     * 对用户请求执行分层路由，返回可绑定给模型的候选工具。
     */
    public Result route(String query, Context ctx) {
        List<ToolCard> visible = hardFilter(registry.allCards(), ctx);
        Set<String> domains = classifier.classify(query);

        List<ToolCard> scoped = visible.stream().filter(card -> card.inDomains(domains)).toList();
        List<ToolRecallStrategy.Scored> recalled = recallStrategy.recall(query, scoped, domains, props.getTopK());

        boolean widened = recalled.size() < props.getMinCandidates();
        if (widened) {
            recalled = recallStrategy.recall(query, visible, domains, props.getExpandedTopK());
        }
        int limit = widened ? props.getExpandedTopK() : props.getTopK();

        double topScore = recalled.isEmpty() ? 0 : recalled.get(0).score();
        boolean lowConfidence = domains.isEmpty() || topScore < props.getLowConfidenceScore();

        LinkedHashMap<String, ToolCard> selected = new LinkedHashMap<>();
        recalled.stream().limit(limit).forEach(scored -> selected.putIfAbsent(scored.card().name(), scored.card()));
        pinned(visible).forEach(card -> selected.putIfAbsent(card.name(), card));
        if (selected.isEmpty()) {
            coreFallback(visible).forEach(card -> selected.putIfAbsent(card.name(), card));
        }

        List<ToolCard> cards = List.copyOf(selected.values());
        int schemaTokens = estimateSchemaTokens(cards);
        metrics.recordRoute(cards.size(), schemaTokens, lowConfidence, widened);
        log.debug("工具路由 query=[{}] domains={} 候选={} schemaTokens≈{} lowConfidence={} widened={}",
                abbreviate(query), domains, selected.keySet(), schemaTokens, lowConfidence, widened);

        return new Result(
                cards.stream().map(ToolCard::name).toList(),
                cards.stream().map(ToolCard::specification).toList(),
                domains,
                lowConfidence,
                schemaTokens);
    }

    /**
     * 执行前二次鉴权（分层第 6 层）：工具存在、未禁用、未被黑名单拦截、角色可见。
     */
    public boolean isAllowed(String toolName, Context ctx) {
        return registry.cardOf(toolName)
                .map(card -> card.enabled() && !blocked(card, ctx) && card.visibleTo(ctx.role()))
                .orElse(false);
    }

    /** 查询工具卡片（执行层区分来源 / 风险时使用） */
    public Optional<ToolCard> cardOf(String toolName) {
        return registry.cardOf(toolName);
    }

    /**
     * 第 2 层：确定性硬过滤。以谓词链组合，避免条件分支堆叠。
     */
    private List<ToolCard> hardFilter(List<ToolCard> cards, Context ctx) {
        Predicate<ToolCard> visible = List.<Predicate<ToolCard>>of(
                        ToolCard::enabled,
                        card -> !props.getBlockedTools().contains(card.name()),
                        card -> !(ctx.isProduction() && props.getProductionBlockedTools().contains(card.name())),
                        card -> !ctx.disabledTools().contains(card.name()),
                        card -> card.visibleTo(ctx.role()))
                .stream()
                .reduce(card -> true, Predicate::and);
        return cards.stream().filter(visible).toList();
    }

    private boolean blocked(ToolCard card, Context ctx) {
        return props.getBlockedTools().contains(card.name())
                || (ctx.isProduction() && props.getProductionBlockedTools().contains(card.name()))
                || ctx.disabledTools().contains(card.name());
    }

    /** 常驻工具：按配置顺序保留可见的高频低风险工具 */
    private List<ToolCard> pinned(List<ToolCard> visible) {
        return props.getPinnedTools().stream()
                .flatMap(name -> visible.stream().filter(card -> card.name().equals(name)))
                .toList();
    }

    /** 兜底：无任何候选时，回退若干个只读工具，保证模型仍有最基本的查询能力 */
    private List<ToolCard> coreFallback(List<ToolCard> visible) {
        return visible.stream().filter(ToolCard::readOnly).limit(3).toList();
    }

    private int estimateSchemaTokens(List<ToolCard> cards) {
        int chars = 0;
        for (ToolCard card : cards) {
            chars += length(card.name()) + length(card.description());
            ToolSpecification spec = card.specification();
            if (spec.parameters() != null && spec.parameters().properties() != null) {
                chars += spec.parameters().properties().keySet().stream().mapToInt(String::length).sum();
            }
        }
        return (int) Math.ceil(chars / 4.0);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String abbreviate(String query) {
        if (query == null) {
            return "";
        }
        return query.length() <= 40 ? query : query.substring(0, 40) + "...";
    }

    /**
     * 工具路由上下文（模块输入）：承载每次请求的确定性硬过滤依据。
     *
     * @param environment   运行环境（如 dev / prod），用于静态 Allowlist/Blocklist
     * @param role          当前用户角色（EMPLOYEE/DEPT_LEADER/HR/HRBP/ADMIN），空表示未登录
     * @param disabledTools 运行时禁用工具集合（功能开关 / 工具健康度熔断）
     */
    public record Context(String environment, String role, Set<String> disabledTools) {

        public Context {
            disabledTools = disabledTools == null ? Set.of() : Set.copyOf(disabledTools);
        }

        public static Context of(String environment, String role) {
            return new Context(environment, role, Set.of());
        }

        public boolean isProduction() {
            return environment != null && environment.equalsIgnoreCase("prod");
        }
    }

    /**
     * 一次工具路由的结果（模块输出）。
     *
     * @param toolNames      本次允许模型调用的候选工具名（供执行层做「候选外拒绝」校验）
     * @param specifications 绑定给模型的候选工具完整 Schema
     * @param domains        本次请求识别到的业务域
     * @param lowConfidence  是否低置信度（未识别到业务域，或召回最高分过低）
     * @param schemaTokens   候选工具 Schema 的估算 token 数（评估用）
     */
    public record Result(
            List<String> toolNames,
            List<ToolSpecification> specifications,
            Set<String> domains,
            boolean lowConfidence,
            int schemaTokens) {
    }
}
