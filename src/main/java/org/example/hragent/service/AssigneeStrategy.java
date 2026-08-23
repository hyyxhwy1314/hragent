package org.example.hragent.service;

import java.util.Map;

/**
 * 单个流程的审批人解析策略
 * <p>
 * 每个流程（入职/转正/调岗/离职）实现一个策略 Bean，由 {@link AssigneeResolver}
 * 按 processKey 路由调用，避免 if-else 分支。
 */
public interface AssigneeStrategy {

    /** 该策略支持的流程定义 key，如 onboard-process */
    String supportProcessKey();

    /**
     * 解析审批人变量
     *
     * @param bizId      业务主键ID
     * @param applyEmpId 申请人员工ID
     * @return 审批人变量映射
     */
    Map<String, String> resolve(Long bizId, Long applyEmpId);
}
