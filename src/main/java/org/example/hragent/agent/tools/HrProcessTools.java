package org.example.hragent.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.dto.TaskCompleteDto;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.entity.LeaveBalance;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.entity.Resume;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.service.FlowInspectionService;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.service.LeaveBalanceService;
import org.example.hragent.service.LeaveRequestService;
import org.example.hragent.service.ResumeService;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.vo.FlowTraceVO;
import org.example.hragent.vo.StuckFlowVO;
import org.example.hragent.vo.TaskVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程工具：离职/调岗/转正流程发起、流程实例与轨迹查询、待办审批、审批摘要与异常巡检。
 */
@Component
public class HrProcessTools implements HrToolSet {

    /** 异常巡检默认超时阈值（小时） */
    private static final int DEFAULT_STUCK_THRESHOLD_HOURS = 48;

    private final FlowOrchestratorService flowOrchestratorService;
    private final FlowInspectionService flowInspectionService;
    private final FlowInstanceMapper flowInstanceMapper;
    private final LeaveRequestService leaveRequestService;
    private final LeaveBalanceService leaveBalanceService;
    private final ResumeService resumeService;
    private final EmployeeService employeeService;
    private final HrToolSupport support;

    public HrProcessTools(FlowOrchestratorService flowOrchestratorService,
                          FlowInspectionService flowInspectionService,
                          FlowInstanceMapper flowInstanceMapper,
                          LeaveRequestService leaveRequestService,
                          LeaveBalanceService leaveBalanceService,
                          ResumeService resumeService,
                          EmployeeService employeeService,
                          HrToolSupport support) {
        this.flowOrchestratorService = flowOrchestratorService;
        this.flowInspectionService = flowInspectionService;
        this.flowInstanceMapper = flowInstanceMapper;
        this.leaveRequestService = leaveRequestService;
        this.leaveBalanceService = leaveBalanceService;
        this.resumeService = resumeService;
        this.employeeService = employeeService;
        this.support = support;
    }

    /** 为指定员工发起离职流程 */
    @Tool("为指定员工发起离职申请流程，参数为员工姓名")
    public String startResignationProcess(String query) {
        return startEmployeeProcess(query, FlowConstants.PROC_KEY_LEAVE, "离职");
    }

    /** 为指定员工发起调岗流程 */
    @Tool("为指定员工发起调岗申请流程，参数为员工姓名")
    public String startTransferProcess(String query) {
        return startEmployeeProcess(query, FlowConstants.PROC_KEY_TRANSFER, "调岗");
    }

    /** 为指定员工发起转正流程 */
    @Tool("为指定员工发起转正申请流程，参数为员工姓名")
    public String startRegularProcess(String query) {
        return startEmployeeProcess(query, FlowConstants.PROC_KEY_REGULAR, "转正");
    }

    /** 发起以员工为业务对象的流程（离职/调岗/转正），三者仅流程键与文案不同 */
    private String startEmployeeProcess(String query, String processKey, String label) {
        try {
            String employeeName = support.extractProcessEmployeeName(query);
            if (employeeName == null || employeeName.isEmpty()) {
                return String.format("请提供员工姓名以发起%s流程，例如：发起张三的%s流程", label, label);
            }
            Employee employee = support.findEmployeeByName(employeeName);
            if (employee == null) {
                return String.format("未找到员工：%s，请确认员工姓名是否正确", employeeName);
            }
            FlowStartDto dto = new FlowStartDto();
            dto.setProcessKey(processKey);
            dto.setBizId(employee.getId());
            flowOrchestratorService.start(dto);
            return String.format("已为员工 %s（工号：%s）发起%s流程，审批已提交至相关负责人。",
                    employee.getEmpName(), employee.getEmpNo(), label);
        } catch (Exception e) {
            return String.format("发起%s流程失败：%s", label, e.getMessage());
        }
    }

    /** 查询审批流程实例列表，可按流程类型或状态筛选 */
    @Tool("查询审批流程实例列表，可按流程类型（入职/离职/调岗/转正）和状态（进行中/已通过/已拒绝/已撤回）筛选")
    public String queryProcessInstances(String query) {
        try {
            String type = support.classifyFlowType(query);
            Integer status = support.classifyFlowStatus(query);
            LambdaQueryWrapper<FlowInstance> wrapper = new LambdaQueryWrapper<FlowInstance>()
                    .eq(type != null, FlowInstance::getFlowType, type)
                    .eq(status != null, FlowInstance::getFlowStatus, status)
                    .orderByDesc(FlowInstance::getId)
                    .last("LIMIT 20");
            List<FlowInstance> instances = flowInstanceMapper.selectList(wrapper);
            if (instances.isEmpty()) {
                return "未查询到符合条件的流程实例";
            }
            StringBuilder sb = new StringBuilder("共 ").append(instances.size()).append(" 条流程实例：\n");
            for (FlowInstance fi : instances) {
                sb.append(String.format("- 单号 %s | 类型 %s | 状态 %s | 业务ID %s | 流程实例 %s\n",
                        fi.getFlowNo(), fi.getFlowType(),
                        support.flowStatusText(fi.getFlowStatus()), fi.getBizId(), fi.getFlowableProcInstId()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询流程实例失败：" + e.getMessage();
        }
    }

    /** 查询指定流程实例的审批轨迹 */
    @Tool("查询指定流程实例的审批轨迹和各节点处理状态，参数为流程实例ID")
    public String queryProcessTrace(String query) {
        try {
            Long id = support.extractLong(query);
            if (id == null) {
                return "请提供流程实例ID以查询审批轨迹，例如：查询流程实例 8 的审批进度";
            }
            List<FlowTraceVO> trace = flowOrchestratorService.getTrace(id);
            if (trace.isEmpty()) {
                return "该流程实例暂无审批记录";
            }
            StringBuilder sb = new StringBuilder("审批轨迹：\n");
            for (FlowTraceVO t : trace) {
                sb.append(String.format("- %s | %s | 处理人 %s | %s%s\n",
                        t.getNodeName(),
                        t.getStartTime() == null ? "未开始" : t.getStartTime().toString().replace("T", " "),
                        support.nvl(t.getAssigneeName()), support.nvl(t.getStatus()),
                        t.getComment() == null ? "" : " | 意见：" + t.getComment()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询流程轨迹失败：" + e.getMessage();
        }
    }

    /** 查询当前登录人的待办审批任务 */
    @Tool("查询当前登录人的待办审批任务列表")
    public String queryMyTodoTasks() {
        try {
            Long empId = CurrentUserService.empId();
            if (empId == null) {
                return "当前未获取到登录用户，无法查询待办";
            }
            List<TaskVO> tasks = flowOrchestratorService.listTodoTasks(empId);
            if (tasks.isEmpty()) {
                return "您当前没有待办审批任务";
            }
            StringBuilder sb = new StringBuilder("您的待办审批任务（共 ").append(tasks.size()).append(" 条）：\n");
            for (TaskVO t : tasks) {
                sb.append(String.format("- 任务 %s | %s | 单号 %s | 任务ID %s\n",
                        support.nvl(t.getTaskName()), support.nvl(t.getFlowType()),
                        support.nvl(support.flowNoOf(t.getFlowInstanceId())), t.getTaskId()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询待办失败：" + e.getMessage();
        }
    }

    /** 完成审批任务（通过/拒绝） */
    @Tool("完成一个审批任务：通过或拒绝。参数需包含任务ID（必填）与审批结果（通过/拒绝），可附审批意见")
    public String completeApprovalTask(String query) {
        try {
            String taskId = support.extractTaskId(query);
            Boolean approved = support.extractApproved(query);
            if (taskId == null || taskId.isEmpty()) {
                return "请提供任务ID与审批结果，例如：通过任务 62505，意见 同意";
            }
            if (approved == null) {
                return "请明确审批结果（通过/拒绝），例如：通过任务 62505";
            }
            String comment = support.extractComment(query);
            TaskCompleteDto dto = new TaskCompleteDto();
            dto.setApproved(approved);
            dto.setComment(comment);
            flowOrchestratorService.completeTask(taskId, dto);
            return String.format("任务 %s 已%s。%s", taskId, approved ? "通过" : "拒绝",
                    comment == null ? "" : "审批意见：" + comment);
        } catch (Exception e) {
            return "完成任务失败：" + e.getMessage();
        }
    }

    /** 审批摘要与风险提示 */
    @Tool("读取流程单据并生成审批摘要与风险提示，辅助审批人快速判断。参数为流程实例ID")
    public String generateApprovalSummary(String query) {
        try {
            Long id = support.extractLong(query);
            if (id == null) {
                return "请提供流程实例ID以生成审批摘要，例如：生成流程 8 的审批摘要";
            }
            FlowInstance fi = flowInstanceMapper.selectById(id);
            if (fi == null) {
                return String.format("未找到流程实例：%s", id);
            }
            Employee applicant = fi.getApplyEmpId() == null ? null : employeeService.getById(fi.getApplyEmpId());
            String applicantName = applicant == null ? "-" : applicant.getEmpName();

            StringBuilder sb = new StringBuilder("【审批摘要】\n");
            sb.append(String.format("- 流程单号：%s\n", fi.getFlowNo()));
            sb.append(String.format("- 流程类型：%s\n", support.flowTypeText(fi.getFlowType())));
            sb.append(String.format("- 申请人：%s\n", applicantName));
            sb.append(String.format("- 当前状态：%s\n", support.flowStatusText(fi.getFlowStatus())));
            sb.append(String.format("- 业务对象：%s\n", bizSummary(fi)));

            String pendingNodes = flowOrchestratorService.getTrace(id).stream()
                    .filter(t -> t.getEndTime() == null)
                    .map(FlowTraceVO::getNodeName)
                    .filter(n -> n != null && !n.isBlank())
                    .distinct()
                    .collect(Collectors.joining("、"));
            if (!pendingNodes.isEmpty()) {
                sb.append(String.format("- 当前节点：%s（待处理）\n", pendingNodes));
            }

            List<String> risks = collectApprovalRisks(fi);
            sb.append("\n【风险提示】\n");
            if (risks.isEmpty()) {
                sb.append("- 暂未发现明显风险，可按常规流程审批。\n");
            } else {
                risks.forEach(r -> sb.append("- ").append(r).append("\n"));
            }
            return sb.toString();
        } catch (Exception e) {
            return "生成审批摘要失败：" + e.getMessage();
        }
    }

    /** 异常巡检：扫描长时间未审批、卡住的进行中流程 */
    @Tool("巡检长时间未审批、异常卡住的流程实例并给出告警清单，可选参数为超时小时数（默认48小时）")
    public String inspectStuckFlows(String query) {
        try {
            int thresholdHours = DEFAULT_STUCK_THRESHOLD_HOURS;
            Long parsed = support.extractLong(query);
            if (parsed != null && parsed > 0 && parsed <= 24 * 30) {
                thresholdHours = parsed.intValue();
            }
            List<StuckFlowVO> stuck = flowInspectionService.scanStuckFlows(thresholdHours);
            if (stuck.isEmpty()) {
                return String.format("流程异常巡检完成：未发现停留超过 %d 小时未审批的流程。", thresholdHours);
            }
            StringBuilder sb = new StringBuilder(String.format(
                    "流程异常巡检发现 %d 个超时未审批流程（阈值 %d 小时）：\n", stuck.size(), thresholdHours));
            for (StuckFlowVO vo : stuck) {
                sb.append(String.format("- 单号 %s | %s | 节点 %s | 处理人 %s | 已停留 %d 小时\n",
                        vo.getFlowNo(), support.flowTypeText(vo.getFlowType()), support.nvl(vo.getCurrentNode()),
                        support.nvl(vo.getAssigneeName()), vo.getPendingHours()));
            }
            sb.append("建议：对以上流程及时催办或转办，避免审批超时影响业务时效。\n");
            return sb.toString();
        } catch (Exception e) {
            return "流程异常巡检失败：" + e.getMessage();
        }
    }

    /** 汇总审批风险点：按流程类型读取业务单据做规则校验 */
    private List<String> collectApprovalRisks(FlowInstance fi) {
        List<String> risks = new ArrayList<>();
        if (FlowConstants.FLOW_TYPE_LEAVE_REQUEST.equals(fi.getFlowType())) {
            LeaveRequest lr = leaveRequestService.getById(fi.getBizId());
            if (lr == null) {
                risks.add("请假单不存在或已被删除，请核实业务数据");
                return risks;
            }
            int year = LocalDate.now().getYear();
            LeaveBalance balance = leaveBalanceService.getOrInit(lr.getEmpId(), year);
            BigDecimal used = balance.getUsedDays() == null ? BigDecimal.ZERO : balance.getUsedDays();
            BigDecimal remaining = balance.getTotalDays().subtract(used);
            BigDecimal days = lr.getLeaveDays() == null ? BigDecimal.ZERO : lr.getLeaveDays();
            if (remaining.compareTo(days) < 0) {
                risks.add(String.format("该员工本年度剩余假期不足：剩余 %s 天，本次申请 %s 天", remaining, days));
            }
            risks.add(String.format("请假时段：%s 至 %s，共 %s 天，类型 %s",
                    lr.getStartDate(), lr.getEndDate(), days, lr.getLeaveType()));
        } else if (FlowConstants.FLOW_TYPE_REGULAR.equals(fi.getFlowType())) {
            Employee emp = employeeService.getById(fi.getBizId());
            if (emp == null) {
                risks.add("转正员工不存在或已被删除，请核实业务数据");
                return risks;
            }
            if (emp.getEmpStatus() == null || emp.getEmpStatus() != HrToolSupport.STATUS_PROBATION) {
                risks.add(String.format("员工当前状态为「%s」，并非试用状态，转正发起条件存疑",
                        support.empStatusText(emp.getEmpStatus())));
            }
            if (emp.getRegularDate() != null && LocalDate.now().isBefore(emp.getRegularDate())) {
                risks.add(String.format("转正日期 %s 尚未到达，提前转正请确认是否符合制度", emp.getRegularDate()));
            }
        } else if (FlowConstants.FLOW_TYPE_ONBOARD.equals(fi.getFlowType())) {
            Resume resume = resumeService.getById(fi.getBizId());
            if (resume == null) {
                risks.add("候选简历不存在或已被删除，请核实业务数据");
            } else if (resume.getResumeStatus() == null || resume.getResumeStatus() != HrToolSupport.RESUME_STATUS_ACCEPTED) {
                risks.add("候选简历当前非「录用」状态，入职前置条件可能不满足");
            }
        }
        return risks;
    }

    /** 业务对象摘要：按类型解析业务单据关键信息 */
    private String bizSummary(FlowInstance fi) {
        if (FlowConstants.FLOW_TYPE_LEAVE_REQUEST.equals(fi.getFlowType())) {
            LeaveRequest lr = leaveRequestService.getById(fi.getBizId());
            return lr == null ? "请假单#" + fi.getBizId()
                    : String.format("请假单#%s（%s %s 至 %s，%s 天）",
                    lr.getId(), lr.getLeaveType(), lr.getStartDate(), lr.getEndDate(), lr.getLeaveDays());
        }
        if (FlowConstants.FLOW_TYPE_ONBOARD.equals(fi.getFlowType())) {
            Resume resume = resumeService.getById(fi.getBizId());
            return resume == null ? "简历#" + fi.getBizId()
                    : String.format("简历#%s（候选人 %s）", resume.getId(), resume.getResumeName());
        }
        Employee emp = employeeService.getById(fi.getBizId());
        return emp == null ? "员工#" + fi.getBizId()
                : String.format("员工#%s（%s，%s）", emp.getId(), emp.getEmpName(), support.nvl(emp.getDeptName()));
    }
}