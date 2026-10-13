package org.example.hragent.service;

import org.example.hragent.vo.StuckFlowVO;

import java.util.List;

/**
 * 流程异常巡检服务：扫描长时间停留未审批的进行中流程实例。
 * <p>
 * 供定时巡检任务（自动告警）与 Agent 巡检工具（按需诊断）复用，
 * 采用游标分页（按 id 递增）逐批处理，避免一次性全表加载造成长事务与锁等待。
 */
public interface FlowInspectionService {

    /**
     * 扫描「进行中」且当前节点停留超过阈值时长的流程实例。
     *
     * @param thresholdHours 超时阈值（小时），停留时长 ≥ 该值视为异常
     * @return 异常流程列表，按停留时长倒序
     */
    List<StuckFlowVO> scanStuckFlows(int thresholdHours);
}