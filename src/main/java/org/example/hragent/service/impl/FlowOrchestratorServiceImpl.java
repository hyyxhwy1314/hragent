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
import org.flowable.task.api.TaskInfo;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowInstanceVO start(FlowStartDto dto) {
        Long applyEmpId = CurrentUserService.empId();
        BusinessException.throwIf(applyEmpId == null, ErrorCode.UNAUTHORIZED);

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
        variables.putAll(assigneeResolver.resolve(dto.getProcessKey(), dto.getBizId(), applyEmpId));

        // 解析 bizJson 平铺为流程变量（HR 发起时可通过 bizJson 指定 targetLeaderId 等业务参数）
        if (dto.getBizJson() != null && !dto.getBizJson().isBlank()) {
            try {
                Map<String, Object> bizVars = objectMapper.readValue(dto.getBizJson(),
                        new TypeReference<Map<String, Object>>() {});
                variables.putAll(bizVars);
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
        flowInstance.setFlowNo(generateFlowNo());
        flowInstance.setFlowType(FlowConstants.flowTypeOf(dto.getProcessKey()));
        flowInstance.setBizId(dto.getBizId());
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
        Long approverEmpId = CurrentUserService.empId();
        BusinessException.throwIf(approverEmpId == null, ErrorCode.UNAUTHORIZED);
        Task task = loadAndCheckTask(taskId, approverEmpId);

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
        Long approverEmpId = CurrentUserService.empId();
        BusinessException.throwIf(approverEmpId == null, ErrorCode.UNAUTHORIZED);
        loadAndCheckTask(taskId, approverEmpId);
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
        return tasks.stream().map(t -> toTaskVO(t, assigneeEmpId)).collect(Collectors.toList());
    }

    @Override
    public List<TaskVO> listDoneTasks(Long assigneeEmpId) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(assigneeEmpId))
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
        return tasks.stream().map(t -> toTaskVO(t, assigneeEmpId)).collect(Collectors.toList());
    }

    @Override
    public List<FlowTraceVO> getTrace(Long flowInstanceId) {
        FlowInstance flowInstance = flowInstanceMapper.selectById(flowInstanceId);
        BusinessException.throwIf(flowInstance == null, ErrorCode.FLOW_INSTANCE_NOT_FOUND);

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

        return historyTasks.stream().map(t -> {
            FlowTraceVO vo = new FlowTraceVO();
            vo.setNodeName(t.getName());
            vo.setAssigneeName(resolveEmpName(t.getAssignee()));
            vo.setStartTime(toLocalDateTime(t.getStartTime()));
            vo.setEndTime(toLocalDateTime(t.getEndTime()));
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

    // ========================= 辅助方法 =========================

    /** 校验任务存在且归属当前审批人 */
    private Task loadAndCheckTask(String taskId, Long approverEmpId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        BusinessException.throwIf(task == null, ErrorCode.FLOW_TASK_NOT_FOUND);
        BusinessException.throwIf(!String.valueOf(approverEmpId).equals(task.getAssignee()),
                ErrorCode.FLOW_TASK_NOT_ASSIGNEE);
        return task;
    }

    /** 按 Flowable 流程实例ID 查业务流程实例 */
    private FlowInstance findFlowInstanceByProcInstId(String procInstId) {
        if (procInstId == null) return null;
        return flowInstanceMapper.selectOne(new LambdaQueryWrapper<FlowInstance>()
                .eq(FlowInstance::getFlowableProcInstId, procInstId));
    }

    /** 按员工ID字符串解析姓名 */
    private String resolveEmpName(String assigneeIdStr) {
        if (assigneeIdStr == null || assigneeIdStr.isBlank()) return null;
        try {
            Employee emp = employeeMapper.selectById(Long.parseLong(assigneeIdStr));
            return emp == null ? null : emp.getEmpName();
        } catch (NumberFormatException e) {
            return null;
        }
    }



    /**
     * 统一的任务视图转换：Task（待办）与 HistoricTaskInstance（已办）均实现 TaskInfo，
     * 合并为单一方法避免重复代码。
     */
    private TaskVO toTaskVO(TaskInfo task, Long currentEmpId) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getId());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setTaskName(task.getName());
        vo.setAssigneeEmpId(currentEmpId);
        vo.setAssigneeName(resolveEmpName(task.getAssignee()));
        vo.setCreateTime(toLocalDateTime(task.getCreateTime()));
        FlowInstance flowInstance = findFlowInstanceByProcInstId(task.getProcessInstanceId());
        if (flowInstance != null) {
            vo.setFlowInstanceId(flowInstance.getId());
            vo.setFlowType(flowInstance.getFlowType());
            vo.setBizId(flowInstance.getBizId());
        }
        return vo;
    }

    /** Flowable Date → LocalDateTime 转换（系统时区） */
    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** 生成业务流水号 FLyyyyMMddHHmmss + 4 位自增（简化实现） */
    private String generateFlowNo() {
        return "FL" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", System.currentTimeMillis() % 10000);
    }
}
