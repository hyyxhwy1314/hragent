package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.service.AssigneeResolver;
import org.example.hragent.service.AssigneeStrategy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审批人解析器默认实现
 * <p>
 * 通过 Spring 注入所有 {@link AssigneeStrategy} Bean，按 supportProcessKey 路由调用，
 * 避免使用 if-else 分支判断流程类型。新增流程只需新增策略 Bean，无需修改本类。
 */
@Slf4j
@Service
public class AssigneeResolverImpl implements AssigneeResolver {

    private final Map<String, AssigneeStrategy> strategyMap;

    public AssigneeResolverImpl(List<AssigneeStrategy> strategies) {
        // 启动时一次性构建 key->策略 映射，运行时 O(1) 路由
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(AssigneeStrategy::supportProcessKey, Function.identity()));
        log.info("审批人策略加载完成 strategies={}", strategyMap.keySet());
    }

    /**
     * 根据流程类型，调用对应的策略实现
     */
    @Override
    public Map<String, String> resolve(String processKey, Long bizId, Long applyEmpId) {
        AssigneeStrategy strategy = strategyMap.get(processKey);
        BusinessException.throwIf(strategy == null, ErrorCode.FLOW_DEFINITION_NOT_FOUND,
                "未找到流程 " + processKey + " 的审批人策略");
        return strategy.resolve(bizId, applyEmpId);
    }
}
