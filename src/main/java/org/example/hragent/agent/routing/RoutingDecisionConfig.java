package org.example.hragent.agent.routing;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 本地决策模型相关策略的装配层。
 * <p>
 * 默认（ai.routing.enabled 非 true）使用规则域分类 {@link ToolClassifier}（其自身即
 * {@link DomainDecisionStrategy} 的 Bean），工具规划则使用空实现 {@link NoopToolPlanningStrategy}；
 * 启用本地决策模型时，注入以规则实现为兜底的 {@link LocalModelDomainDecisionStrategy} 并标记 @Primary
 * 覆盖默认实现；再启用本地工具规划（ai.routing.planning.enabled=true）时，注入
 * {@link LocalModelToolPlanningStrategy}（只对只读候选规划，失败自动回退）并标记 @Primary。
 * 通过装配层而非运行时条件分支完成切换，同一切换点的实现实现同一接口，上层无感。
 */
@Configuration
public class RoutingDecisionConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.routing.enabled", havingValue = "true")
    public DomainDecisionStrategy localModelDomainDecisionStrategy(
            @Qualifier("routingChatModel") ChatLanguageModel routingChatModel,
            ToolClassifier ruleClassifier) {
        return new LocalModelDomainDecisionStrategy(routingChatModel, ruleClassifier);
    }

    /**
     * 本地工具规划策略：让本地模型接管「只读工具」的选工具 + 生成参数这一跳，命中时跳过远程模型首跳。
     * 依赖 {@code ai.routing.enabled=true} 提供的 {@code routingChatModel}，故两个开关都需为 true。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = {"ai.routing.enabled", "ai.routing.planning.enabled"}, havingValue = "true")
    public ToolPlanningStrategy localModelToolPlanningStrategy(
            @Qualifier("routingChatModel") ChatLanguageModel routingChatModel) {
        return new LocalModelToolPlanningStrategy(routingChatModel);
    }
}
