package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.service.AssigneeResolver;
import org.example.hragent.service.AssigneeStrategy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
     * <p>
     * 业务人员通过设计器自建的流程通常没有专属审批人策略，此时按发起人本人兜底，
     * 保证 BPMN 中 ${approver_xxx} 变量可正常解析、流程可发起并流转；
     * 内置流程仍走各自策略，行为不变。
     */
    @Override
    public Map<String, String> resolve(String processKey, Long bizId, Long applyEmpId) {
        AssigneeStrategy strategy = strategyMap.get(processKey);
        if (strategy == null) {
            log.warn("流程 {} 无专属审批人策略，按发起人 {} 本人兜底（适用于自建流程）", processKey, applyEmpId);
            return selfFallback(applyEmpId);
        }
        return strategy.resolve(bizId, applyEmpId);
    }

    /** 无策略流程的兜底审批人：发起人本人 */
    private Map<String, String> selfFallback(Long applyEmpId) {
        String self = String.valueOf(applyEmpId);
        Map<String, String> fallback = new HashMap<>();
        fallback.put("approver_dept", self);
        fallback.put("approver_hr", self);
        fallback.put("approver_director", self);
        fallback.put("targetLeaderId", self);
        return fallback;
    }
}
