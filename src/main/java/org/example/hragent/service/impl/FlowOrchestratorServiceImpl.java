package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.converter.FlowInstanceConverter;
import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.dto.TaskCompleteDto;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.FlowApproval;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.mapper.FlowApprovalMapper;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.service.AssigneeResolver;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.utils.FlowNoGenerator;
import org.example.hragent.utils.FlowableUtils;
import org.example.hragent.vo.FlowCcVO;
import org.example.hragent.vo.FlowDiagramVO;
import org.example.hragent.vo.FlowInstanceVO;
import org.example.hragent.vo.FlowTraceVO;
import org.example.hragent.vo.TaskVO;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程编排服务实现：业务层 ↔ Flowable 引擎桥接
 * <p>
 * 关键设计：
 * <ul>
 *   <li>所有写方法加 {@link Transactional}，业务表与流程表同事务原子提交</li>
 *   <li>审批人变量由 {@link AssigneeResolver} 预填，BPMN 中 ${approver_xxx} 取值</li>
 *   <li>每次审批写入 t_flow_approval 留痕，轨迹查询按 task_id 精确匹配</li>
 *   <li>错误统一抛 {@link BusinessException}，由全局异常处理器转 JSON</li>
 * </ul>
 * 视图装配（任务/抄送/流程图）与权限校验下沉至 {@link FlowTaskViewAssembler}、
 * {@link FlowCcSupport}、{@link FlowDiagramSupport}、{@link FlowAccessGuard}，本类只保留发起/审批/撤回/轨迹编排。
 */
@Slf4j
@Service
public class FlowOrchestratorServiceImpl implements FlowOrchestratorService {

    @Autowired private RuntimeService runtimeService;
    @Autowired private TaskService taskService;
    @Autowired private HistoryService historyService;
    @Autowired private RepositoryService repositoryService;
    @Autowired private AssigneeResolver assigneeResolver;
    @Autowired private FlowInstanceMapper flowInstanceMapper;
    @Autowired private FlowApprovalMapper flowApprovalMapper;
    @Autowired private EmployeeMapper employeeMapper;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FlowInstanceConverter flowInstanceConverter;
    @Autowired private EmployeeNameResolver employeeNameResolver;
    @Autowired private FlowTaskViewAssembler flowTaskViewAssembler;
    @Autowired private FlowCcSupport flowCcSupport;
    @Autowired private FlowDiagramSupport flowDiagramSupport;
    @Autowired private FlowAccessGuard flowAccessGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowInstanceVO start(FlowStartDto dto) {
        Long applyEmpId = CurrentUserService.requireEmpId();

        // 业务幂等：同一业务对象已存在进行中流程时拒绝重复发起，避免并发/重复点击产生多实例
        // 不关联业务对象（bizId 为空）时以哨兵值 -1 参与匹配（-1 非合法主键，等价于跳过该项校验）
        long runningCount = flowInstanceMapper.selectCount(new LambdaQueryWrapper<FlowInstance>()
                .eq(FlowInstance::getFlowType, FlowConstants.flowTypeOf(dto.getProcessKey()))
                .eq(FlowInstance::getBizId, dto.getBizId() == null ? -1L : dto.getBizId())
                .eq(FlowInstance::getFlowStatus, FlowConstants.STATUS_RUNNING));
        BusinessException.throwIf(runningCount > 0, ErrorCode.FLOW_DUPLICATE_START);

        // 校验流程定义存在
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(dto.getProcessKey())
                .latestVersion()
                .singleResult();
        BusinessException.throwIf(def == null, ErrorCode.FLOW_DEFINITION_NOT_FOUND,
                "流程定义不存在: " + dto.getProcessKey());

        // 预填审批人变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("bizId", dto.getBizId());
        variables.put("applyEmpId", applyEmpId);
        // 流程类型变量：供审批通过后的自动节点（如结果通知邮件）选用对应业务文案
        variables.put("flowType", FlowConstants.flowTypeOf(dto.getProcessKey()));
        variables.putAll(assigneeResolver.resolve(dto.getProcessKey(), dto.getBizId(), applyEmpId));

        // 解析 bizJson 平铺为流程变量（HR 发起时可通过 bizJson 指定 targetLeaderId 等业务参数）
        // 仅补充服务端未设置的变量，防止客户端覆盖 bizId/applyEmpId/flowType/审批人等关键变量
        if (dto.getBizJson() != null && !dto.getBizJson().isBlank()) {
            try {
                Map<String, Object> bizVars = objectMapper.readValue(dto.getBizJson(),
                        new TypeReference<Map<String, Object>>() {});
                bizVars.forEach(variables::putIfAbsent);
            } catch (Exception e) {
                log.warn("bizJson 解析失败，已忽略: {}", dto.getBizJson(), e);
            }
        }

        // 发起 Flowable 流程实例
        ProcessInstance procInst;
        try {
            procInst = runtimeService.startProcessInstanceByKey(dto.getProcessKey(), variables);
        } catch (Exception e) {
            log.error("流程发起失败 processKey={}, bizId={}", dto.getProcessKey(), dto.getBizId(), e);
            throw new BusinessException(ErrorCode.FLOW_START_FAILED, e.getMessage());
        }

        // 写业务流程实例表
        FlowInstance flowInstance = new FlowInstance();
        flowInstance.setFlowNo(FlowNoGenerator.generate());
        flowInstance.setFlowType(FlowConstants.flowTypeOf(dto.getProcessKey()));
        flowInstance.setBizId(dto.getBizId());
        // 业务对象类型：自定义流程由前端指定；内置流程按 processKey 推断
        flowInstance.setBizType(dto.getBizType() != null ? dto.getBizType()
                : FlowConstants.bizTypeOf(dto.getProcessKey()));
        flowInstance.setApplyEmpId(applyEmpId);
        flowInstance.setFlowStatus(FlowConstants.STATUS_RUNNING);
        flowInstance.setFlowableProcInstId(procInst.getId());
        flowInstance.setBizJson(dto.getBizJson());
        flowInstanceMapper.insert(flowInstance);

        log.info("流程发起成功 processKey={}, flowInstanceId={}, procInstId={}",
                dto.getProcessKey(), flowInstance.getId(), procInst.getId());
        return flowInstanceConverter.entityToVo(flowInstance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeTask(String taskId, TaskCompleteDto dto) {
        return doComplete(taskId, CurrentUserService.requireEmpId(), dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeTaskAs(String taskId, Long approverEmpId, TaskCompleteDto dto) {
        // 无登录态入口（如飞书卡片回调）：审批人由调用方显式传入，后续仍需归属校验
        BusinessException.throwIf(approverEmpId == null, ErrorCode.PARAM_ERROR, "审批人不能为空");
        return doComplete(taskId, approverEmpId, dto);
    }

    @Override
    public boolean isTaskPending(String taskId) {
        // 任务存在且未处理 → 待处理；任务不存在（已处理/已结束）→ 返回 false
        return taskService.createTaskQuery().taskId(taskId).count() > 0;
    }

    /** 完成任务核心逻辑：任务归属校验 → 写入审批变量与意见 → 完成 → 写审批记录与状态同步 */
    private boolean doComplete(String taskId, Long approverEmpId, TaskCompleteDto dto) {
        // 幂等：任务已被处理（重复点击/并发双提交/网络重试）时直接返回成功，不重复写审批记录、不二次流转
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            boolean handled = historyService.createHistoricTaskInstanceQuery().taskId(taskId).count() > 0;
            BusinessException.throwIf(!handled, ErrorCode.FLOW_TASK_NOT_FOUND);
            log.info("审批重复提交，任务已处理完毕，幂等返回 taskId={}, operator={}", taskId, approverEmpId);
            return true;
        }
        BusinessException.throwIf(!String.valueOf(approverEmpId).equals(task.getAssignee()),
                ErrorCode.FLOW_TASK_NOT_ASSIGNEE);

        // 写入审批结果变量，供 BPMN 网关判断 approved
        Map<String, Object> vars = new HashMap<>();
        vars.put("approved", dto.getApproved());

        // 审批意见同时写入 Flowable 注释（历史留痕）
        if (dto.getComment() != null && !dto.getComment().isBlank()) {
            taskService.addComment(taskId, task.getProcessInstanceId(), dto.getComment());
        }

        taskService.complete(taskId, vars);
        log.info("任务完成 taskId={}, approved={}, approverEmpId={}", taskId, dto.getApproved(), approverEmpId);

        // 写审批记录到 t_flow_approval（按 task_id 精确留痕，轨迹查询用）
        FlowInstance flowInst = findFlowInstanceByProcInstId(task.getProcessInstanceId());
        if (flowInst != null) {
            FlowApproval approval = new FlowApproval();
            approval.setFlowInstanceId(flowInst.getId());
            approval.setTaskId(taskId);
            approval.setNodeName(task.getName());
            approval.setApproverEmpId(approverEmpId);
            approval.setAction(dto.getApproved() ? 1 : 2);
            approval.setComment(dto.getComment());
            flowApprovalMapper.insert(approval);

            // 抄送：本节点审批完成时写入抄送记录（仅知悉，不参与流转）
            flowCcSupport.saveRecords(flowInst.getId(), task.getName(), dto.getCcEmpIds());

            // 同步业务表状态：查 Flowable 实例是否还活着
            // 死了=流程结束，按本次 approved 判断 APPROVED/REJECTED
            // 活着=流转到下一节点，保持 RUNNING 不动
            ProcessInstance stillRunning = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
            if (stillRunning == null) {
                int newStatus = dto.getApproved()
                        ? FlowConstants.STATUS_APPROVED
                        : FlowConstants.STATUS_REJECTED;
                flowInst.setFlowStatus(newStatus);
                flowInstanceMapper.updateById(flowInst);
                log.info("流程已结束 procInstId={}, approved={}, 最终状态={}",
                        task.getProcessInstanceId(), dto.getApproved(), newStatus);
            }
        }

        // 流程状态同步已在上面处理（查实例是否销毁 + 按 approved 判断）
        return true;
    }

    /**
     * 任务转办
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delegateTask(String taskId, TaskCompleteDto dto) {
        Long approverEmpId = CurrentUserService.requireEmpId();

        // 幂等：任务已结束则无需转办，直接返回成功
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            boolean handled = historyService.createHistoricTaskInstanceQuery().taskId(taskId).count() > 0;
            BusinessException.throwIf(!handled, ErrorCode.FLOW_TASK_NOT_FOUND);
            log.info("任务转办重复提交，任务已处理完毕，幂等返回 taskId={}", taskId);
            return true;
        }
        BusinessException.throwIf(!String.valueOf(approverEmpId).equals(task.getAssignee()),
                ErrorCode.FLOW_TASK_NOT_ASSIGNEE);
        BusinessException.throwIf(dto.getDelegateToEmpId() == null, ErrorCode.PARAM_ERROR, "转办目标员工不能为空");

        Employee target = employeeMapper.selectById(dto.getDelegateToEmpId());
        BusinessException.throwIf(target == null, ErrorCode.EMP_NOT_EXIST , "转办目标员工不存在");
        // 转办
        taskService.setAssignee(taskId, String.valueOf(dto.getDelegateToEmpId()));
        log.info("任务转办 taskId={}, from={}, to={}", taskId, approverEmpId, dto.getDelegateToEmpId());
        return true;
    }

    /**
     * 列出待办任务
     */
    @Override
    public List<TaskVO> listTodoTasks(Long assigneeEmpId) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(String.valueOf(assigneeEmpId))
                .orderByTaskCreateTime().desc()
                .list();
        return flowTaskViewAssembler.toTaskVOs(tasks, assigneeEmpId);
    }

    @Override
    public List<TaskVO> listDoneTasks(Long assigneeEmpId) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(assigneeEmpId))
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
        return flowTaskViewAssembler.toTaskVOs(tasks, assigneeEmpId);
    }

    @Override
    public List<FlowTraceVO> getTrace(Long flowInstanceId) {
        FlowInstance flowInstance = flowInstanceMapper.selectById(flowInstanceId);
        BusinessException.throwIf(flowInstance == null, ErrorCode.FLOW_INSTANCE_NOT_FOUND);
        flowAccessGuard.assertAccess(flowInstance, CurrentUserService.empId());

        String procInstId = flowInstance.getFlowableProcInstId();

        // 历史任务按开始时间升序
        List<HistoricTaskInstance> historyTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(procInstId)
                .orderByHistoricTaskInstanceStartTime().asc()
                .list();

        // t_flow_approval 按 task_id 精确匹配每个节点的审批结果
        Map<String, FlowApproval> approvalMap = flowApprovalMapper.selectList(
                        new LambdaQueryWrapper<FlowApproval>()
                                .eq(FlowApproval::getFlowInstanceId, flowInstanceId))
                .stream().collect(Collectors.toMap(FlowApproval::getTaskId, a -> a, (a, b) -> b));

        // 批量解析处理人姓名，避免逐条查询（N+1）
        Set<Long> assigneeIds = historyTasks.stream()
                .map(t -> FlowableUtils.parseEmpId(t.getAssignee()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> assigneeNames = employeeNameResolver.resolveNames(assigneeIds);

        return historyTasks.stream().map(t -> {
            FlowTraceVO vo = new FlowTraceVO();
            vo.setNodeName(t.getName());
            vo.setAssigneeName(assigneeNames.get(FlowableUtils.parseEmpId(t.getAssignee())));
            vo.setStartTime(FlowableUtils.toLocalDateTime(t.getStartTime()));
            vo.setEndTime(FlowableUtils.toLocalDateTime(t.getEndTime()));
            FlowApproval ap = approvalMap.get(t.getId());
            if (t.getEndTime() == null) {
                vo.setStatus("待处理");
            } else if (ap != null) {
                boolean approved = ap.getAction() != null && ap.getAction() == 1;
                vo.setApproved(approved);
                vo.setComment(ap.getComment());
                vo.setStatus(approved ? "已通过" : "已拒绝");
            } else {
                // 旧数据兜底：从注释读
                List<org.flowable.engine.task.Comment> comments = taskService.getTaskComments(t.getId());
                if (comments != null && !comments.isEmpty()) {
                    vo.setComment(comments.get(0).getFullMessage());
                }
                vo.setStatus("已处理");
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long flowInstanceId, Long operatorEmpId) {
        FlowInstance flowInstance = flowInstanceMapper.selectById(flowInstanceId);
        BusinessException.throwIf(flowInstance == null, ErrorCode.FLOW_INSTANCE_NOT_FOUND);
        BusinessException.throwIf(flowInstance.getFlowStatus() != FlowConstants.STATUS_RUNNING,
                ErrorCode.FLOW_ALREADY_FINISHED);
        // 仅申请人本人或 HR/HRBP/ADMIN 可撤回
        flowAccessGuard.assertAccess(flowInstance, operatorEmpId);

        String procInstId = flowInstance.getFlowableProcInstId();
        // 先查 Flowable 实例是否还活着（可能已结束但业务表状态未更新）
        ProcessInstance stillRunning = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId)
                .singleResult();
        if (stillRunning != null) {
            runtimeService.deleteProcessInstance(procInstId, "用户撤回 operator=" + operatorEmpId);
        }
        // 手动更新业务表状态为已撤回
        flowInstance.setFlowStatus(FlowConstants.STATUS_CANCELED);
        flowInstanceMapper.updateById(flowInstance);
        log.info("流程撤回 flowInstanceId={}, operator={}", flowInstanceId, operatorEmpId);
        return true;
    }

    @Override
    public List<FlowCcVO> listMyCc(Long empId) {
        return flowCcSupport.listMyCc(empId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markCcRead(Long ccId, Long empId) {
        return flowCcSupport.markCcRead(ccId, empId);
    }

    @Override
    public FlowDiagramVO getDiagram(Long flowInstanceId) {
        return flowDiagramSupport.getDiagram(flowInstanceId);
    }

    // ========================= 辅助方法 =========================

    /** 按 Flowable 流程实例ID 查业务流程实例 */
    private FlowInstance findFlowInstanceByProcInstId(String procInstId) {
        if (procInstId == null) return null;
        return flowInstanceMapper.selectOne(new LambdaQueryWrapper<FlowInstance>()
                .eq(FlowInstance::getFlowableProcInstId, procInstId));
    }
}
