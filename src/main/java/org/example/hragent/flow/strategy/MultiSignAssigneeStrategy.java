package org.example.hragent.flow.strategy;

import org.example.hragent.constant.FlowConstants;
import org.example.hragent.service.AssigneeStrategy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 多人会签/或签流程审批人解析策略
 * <p>
 * 该流程的审批人不是固定岗位/角色，而是由发起方通过 bizJson 传入的
 * signerList（会签人列表）与 orSignerList（或签人列表）决定，
 * 因此在审批人解析阶段不产生任何默认变量，直接返回空映射，
 * 列表变量由 {@code FlowOrchestratorServiceImpl#start} 解析 bizJson 时注入。
 */
@Component
public class MultiSignAssigneeStrategy implements AssigneeStrategy {

    @Override
    public String supportProcessKey() {
        return FlowConstants.PROC_KEY_MULTI_SIGN;
    }

    @Override
    public Map<String, String> resolve(Long bizId, Long applyEmpId) {
        return Collections.emptyMap();
    }
}