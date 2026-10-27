package org.example.hragent.agent.routing;

import java.util.Set;

/**
 * 业务域决策策略——分层 Tool Routing 第 3 层「粗分类」的可替换实现。
 * <p>
 * 默认实现为规则匹配（{@link ToolClassifier}）；启用本地轻量决策模型后，
 * 装配层切换为 {@link LocalModelDomainDecisionStrategy}，并以规则实现作为兜底。
 * 上层 {@link ToolRouter} 只依赖本接口，因此两种实现可自由切换，不影响召回与执行链路。
 */
public interface DomainDecisionStrategy {

    /**
     * 识别请求命中的业务域集合。
     *
     * @return 命中的业务域；空集表示「不确定」，由召回层跳过域收缩
     */
    Set<String> decide(String query);
}
