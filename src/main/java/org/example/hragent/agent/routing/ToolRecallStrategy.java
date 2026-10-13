package org.example.hragent.agent.routing;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 工具召回策略（分层 Tool Routing 第 4 层）。
 * <p>
 * 目标是「别漏掉正确工具」，只负责打分排序产出候选，不负责执行。
 * 默认实现为 {@link Bm25ToolRecallStrategy}（纯本地 BM25 关键词打分）；
 * 如需接入语义检索，实现本接口并替换注入即可。
 */
public interface ToolRecallStrategy {

    /** 召回结果：工具卡片 + 相关性得分 */
    record Scored(ToolCard card, double score) {
    }

    /**
     * 从候选工具中召回与查询最相关的 Top-K。
     *
     * @param query      用户原始查询
     * @param candidates 已通过硬过滤 / 粗分类收缩的候选工具
     * @param domains    本次请求命中的业务域（用于域命中加权；空集表示不限域）
     * @param topK       返回条数上限
     * @return 按相关性降序的召回结果（可能为空）
     */
    List<Scored> recall(String query, Collection<ToolCard> candidates, Set<String> domains, int topK);
}