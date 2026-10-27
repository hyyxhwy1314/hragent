package org.example.hragent.service;

import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.dto.TaskCompleteDto;
import org.example.hragent.vo.FlowCcVO;
import org.example.hragent.vo.FlowDiagramVO;
import org.example.hragent.vo.FlowInstanceVO;
import org.example.hragent.vo.FlowTraceVO;
import org.example.hragent.vo.TaskVO;

import java.util.List;

/**
 * 流程编排服务：业务层 ↔ Flowable 引擎桥接
 * <p>
 * 封装流程发起、任务处理、流程查询等核心能力，Controller 不直接调用 Flowable API。
 */
public interface FlowOrchestratorService {

    /**
     * 发起流程
     * <p>
     * 内部链路：
     * <ol>
     *   <li>{@link AssigneeResolver} 预填审批人变量</li>
     *   <li>{@link org.flowable.engine.RuntimeService#startProcessInstanceByKey} 发起</li>
     *   <li>写 t_flow_instance 业务实例表</li>
     * </ol>
     */
    FlowInstanceVO start(FlowStartDto dto);

    /**
     * 完成任务（通过/拒绝）
     * <p>
     * 内部链路：
     * <ol>
     *   <li>校验任务归属</li>
     *   <li>写入审批变量 approved 与审批意见</li>
     *   <li>{@link org.flowable.engine.TaskService#complete} 完成</li>
     *   <li>同步更新 t_flow_instance.flowStatus</li>
     * </ol>
     */
    boolean completeTask(String taskId, TaskCompleteDto dto);

    /**
     * 以指定审批人身份完成任务（供无登录态的入口调用，如飞书卡片回调）。
     * <p>
     * 与 {@link #completeTask} 共用同一套处理逻辑，仅审批人来源不同：本方法由调用方显式传入，
     * 仍需通过「任务归属」校验（显式审批人须为该任务 assignee）。
     */
    boolean completeTaskAs(String taskId, Long approverEmpId, TaskCompleteDto dto);

    /**
     * 判断审批任务是否仍处于待处理状态（供无登录态入口做重复提交拦截，如飞书卡片回调）。
     *
     * @return true=任务存在且未处理；false=任务不存在或已被处理
     */
    boolean isTaskPending(String taskId);

    /** 转办任务 */
    boolean delegateTask(String taskId, TaskCompleteDto dto);

    /** 查询待办任务 */
    List<TaskVO> listTodoTasks(Long assigneeEmpId);

    /** 查询已办任务 */
    List<TaskVO> listDoneTasks(Long assigneeEmpId);

    /** 查询流程轨迹 */
    List<FlowTraceVO> getTrace(Long flowInstanceId);

    /** 撤回流程 */
    boolean cancel(Long flowInstanceId, Long operatorEmpId);

    /** 查询抄送给我的流程列表 */
    List<FlowCcVO> listMyCc(Long empId);

    /** 标记抄送记录已读 */
    boolean markCcRead(Long ccId, Long empId);

    /** 查询流程运行态图（BPMN XML + 活动/已完成节点高亮） */
    FlowDiagramVO getDiagram(Long flowInstanceId);
}
