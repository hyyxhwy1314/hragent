package org.example.hragent.service;

import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.dto.TaskCompleteDto;
import org.example.hragent.vo.FlowInstanceVO;
import org.example.hragent.vo.FlowTraceVO;
import org.example.hragent.vo.TaskVO;
import org.flowable.task.api.Task;

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
}
