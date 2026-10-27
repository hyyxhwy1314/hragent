package org.example.hragent.feishu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.config.FeishuOpenApiProperties;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.CertificateRequest;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.entity.Resume;
import org.example.hragent.mapper.CertificateRequestMapper;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.mapper.LeaveRequestMapper;
import org.example.hragent.mapper.ResumeMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 审批待办飞书卡片推送服务。
 * <p>
 * 审批任务创建时（由 {@link FlowTaskCreatedPushListener} 触发），按审批人员工手机号/邮箱解析出飞书
 * open_id 后推送含「同意 / 拒绝」按钮的交互卡片。覆盖全部流程类型（入职/转正/调岗/离职/请假/证明/会签
 * 及自定义流程），按业务对象类型渲染对应摘要。全程异步执行且失败仅记日志，不影响流程主链路。
 */
@Slf4j
@Service
public class FeishuApprovalPushService {

    /** 卡片结果提示中的时间格式（分钟级） */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FeishuOpenApiProperties props;
    private final FeishuCardClient cardClient;
    private final ObjectMapper objectMapper;
    private final FlowInstanceMapper flowInstanceMapper;
    private final LeaveRequestMapper leaveRequestMapper;
    private final CertificateRequestMapper certificateRequestMapper;
    private final ResumeMapper resumeMapper;
    private final EmployeeMapper employeeMapper;

    public FeishuApprovalPushService(FeishuOpenApiProperties props,
                                     FeishuCardClient cardClient,
                                     ObjectMapper objectMapper,
                                     FlowInstanceMapper flowInstanceMapper,
                                     LeaveRequestMapper leaveRequestMapper,
                                     CertificateRequestMapper certificateRequestMapper,
                                     ResumeMapper resumeMapper,
                                     EmployeeMapper employeeMapper) {
        this.props = props;
        this.cardClient = cardClient;
        this.objectMapper = objectMapper;
        this.flowInstanceMapper = flowInstanceMapper;
        this.leaveRequestMapper = leaveRequestMapper;
        this.certificateRequestMapper = certificateRequestMapper;
        this.resumeMapper = resumeMapper;
        this.employeeMapper = employeeMapper;
    }

    /**
     * 业务事务提交后异步推送卡片。
     * <p>
     * 挂在 {@link TransactionalEventListener} 的 {@code AFTER_COMMIT} 阶段，确保读取业务单据/流程实例时
     * 相关数据已提交；{@code fallbackExecution=true} 兼容无事务场景。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApprovalTaskCreated(ApprovalTaskCreatedEvent event) {
        pushApprovalCard(event.taskId(), event.approverEmpId(), event.procInstId(), event.taskName());
    }

    /**
     * 推送审批卡片
     *
     * @param taskId        Flowable 任务ID
     * @param approverEmpId 审批人员工ID
     * @param procInstId    Flowable 流程实例ID
     * @param taskName      节点名称
     */
    public void pushApprovalCard(String taskId, Long approverEmpId, String procInstId, String taskName) {
        try {
            if (!props.isEnabled() || !props.hasCredentials()) {
                return;
            }
            Employee approver = employeeMapper.selectById(approverEmpId);
            String openId = resolveApproverOpenId(approver);
            if (openId == null) {
                // 可观测：记录审批人联系方式，便于排查飞书账号未绑定/权限未开通
                log.warn("审批人未匹配到飞书账号，跳过卡片推送 taskId={}, approverEmpId={}, phone={}, email={}",
                        taskId, approverEmpId,
                        approver == null ? null : approver.getPhone(),
                        approver == null ? null : approver.getEmail());
                return;
            }

            FlowInstance instance = loadFlowInstance(procInstId);
            String title = flowLabel(instance) + "审批待办";
            String content = buildContent(instance, taskName);
            String cardJson = ApprovalCardBuilder.build(taskId, approverEmpId, openId, procInstId,
                    taskName, title, content, objectMapper);
            boolean ok = cardClient.sendInteractiveCard(openId, "open_id", cardJson);
            log.info("审批卡片推送{} taskId={}, node={}, openId={}", ok ? "成功" : "失败", taskId, taskName, openId);
        } catch (Exception e) {
            // 推送失败不影响流程主链路
            log.error("审批卡片推送异常 taskId={}, approverEmpId={}", taskId, approverEmpId, e);
        }
    }

    /**
     * 构建「审批完成」结果卡片（回调响应中返回，替换原卡片使按钮消失，避免重复点击）。
     *
     * @param procInstId Flowable 流程实例ID，用于重建原卡片正文
     * @param taskName   节点名称
     * @param approved   审批结果：true=已同意 / false=已拒绝
     */
    public String buildCompletedCardJson(String procInstId, String taskName, boolean approved) {
        FlowInstance instance = loadFlowInstance(procInstId);
        String time = LocalDateTime.now().format(TIME_FORMATTER);
        return ApprovalCardBuilder.buildResolved(
                approved ? "green" : "red",
                flowLabel(instance) + (approved ? " · 已同意" : " · 已拒绝"),
                contentOf(instance, taskName),
                "该审批已于 " + time + " 处理完成，请勿重复操作。",
                objectMapper);
    }

    /**
     * 构建「已处理过」结果卡片（重复点击被拦截时返回，避免误显示为本次处理结果）。
     *
     * @param procInstId Flowable 流程实例ID，用于重建原卡片正文
     * @param taskName   节点名称
     */
    public String buildAlreadyHandledCardJson(String procInstId, String taskName) {
        FlowInstance instance = loadFlowInstance(procInstId);
        return ApprovalCardBuilder.buildResolved(
                "grey", flowLabel(instance) + " · 已处理",
                contentOf(instance, taskName),
                "该审批此前已被处理，无需重复操作。", objectMapper);
    }

    /** 卡片正文：流程实例为空时给出兜底文案，避免查询到无关数据 */
    private String contentOf(FlowInstance instance, String taskName) {
        if (instance == null) {
            return nodeLine(taskName) + "\n该审批已处理。";
        }
        return buildContent(instance, taskName);
    }

    private FlowInstance loadFlowInstance(String procInstId) {
        if (procInstId == null || procInstId.isBlank()) {
            return null;
        }
        return flowInstanceMapper.selectOne(new LambdaQueryWrapper<FlowInstance>()
                .eq(FlowInstance::getFlowableProcInstId, procInstId));
    }

    /**
     * 按业务对象类型组装卡片正文：请假/证明/入职渲染业务摘要，其余流程渲染员工信息。
     */
    private String buildContent(FlowInstance instance, String taskName) {
        StringBuilder sb = new StringBuilder(nodeLine(taskName)).append("\n");
        if (instance == null) {
            return sb.append("有一条审批待办，请及时处理。").toString();
        }
        sb.append("**流程类型**：").append(flowLabel(instance))
                .append("（").append(nullSafe(instance.getFlowNo(), "-")).append("）\n");

        Employee applicant = employeeMapper.selectById(instance.getApplyEmpId());
        sb.append("**发起人**：").append(employeeLabel(applicant)).append("\n");

        switch (instance.resolvedBizType()) {
            case FlowConstants.BIZ_TYPE_LEAVE_REQUEST -> appendLeave(sb, instance.getBizId());
            case FlowConstants.BIZ_TYPE_CERTIFICATE -> appendCertificate(sb, instance.getBizId());
            case FlowConstants.BIZ_TYPE_RESUME -> appendCandidate(sb, instance.getBizId());
            default -> appendEmployeeBiz(sb, instance.getBizId());
        }
        return sb.append("\n请核对信息后完成审批。").toString();
    }

    private String nodeLine(String taskName) {
        return taskName == null || taskName.isBlank() ? "**当前节点**：审批" : "**当前节点**：" + taskName;
    }

    private void appendLeave(StringBuilder sb, Long bizId) {
        LeaveRequest request = bizId == null ? null : leaveRequestMapper.selectById(bizId);
        if (request == null) {
            return;
        }
        sb.append("**请假类型**：").append(nullSafe(request.getLeaveType(), "-")).append("\n")
                .append("**请假时间**：").append(nullSafe(request.getStartDate(), "-"))
                .append(" 至 ").append(nullSafe(request.getEndDate(), "-"))
                .append("（").append(formatDays(request.getLeaveDays())).append(" 天）\n")
                .append("**请假事由**：").append(nullSafe(request.getReason(), "无")).append("\n");
    }

    private void appendCertificate(StringBuilder sb, Long bizId) {
        CertificateRequest request = bizId == null ? null : certificateRequestMapper.selectById(bizId);
        if (request == null) {
            return;
        }
        sb.append("**证明类型**：").append(nullSafe(request.getCertType(), "-")).append("\n")
                .append("**用途说明**：").append(nullSafe(request.getPurpose(), "无")).append("\n");
    }

    private void appendCandidate(StringBuilder sb, Long bizId) {
        Resume resume = bizId == null ? null : resumeMapper.selectById(bizId);
        if (resume == null) {
            return;
        }
        sb.append("**候选人**：").append(nullSafe(resume.getResumeName(), "-")).append("\n")
                .append("**意向岗位**：").append(nullSafe(resume.getExpectPosition(), "-")).append("\n")
                .append("**意向城市**：").append(nullSafe(resume.getExpectCity(), "-")).append("\n");
    }

    private void appendEmployeeBiz(StringBuilder sb, Long bizId) {
        Employee target = bizId == null ? null : employeeMapper.selectById(bizId);
        if (target == null) {
            return;
        }
        sb.append("**员工**：").append(employeeLabel(target)).append("\n")
                .append("**岗位**：").append(nullSafe(target.getPositionName(), "-")).append("\n")
                .append("**入职日期**：").append(nullSafe(target.getEntryDate(), "-")).append("\n");
        if (target.getRegularDate() != null) {
            sb.append("**转正日期**：").append(target.getRegularDate()).append("\n");
        }
        if (target.getLeaveDate() != null) {
            sb.append("**离职日期**：").append(target.getLeaveDate()).append("\n");
        }
    }

    /** 员工展示名：姓名（部门） */
    private String employeeLabel(Employee employee) {
        if (employee == null) {
            return "-";
        }
        String dept = employee.getDeptName();
        return dept == null || dept.isBlank()
                ? nullSafe(employee.getEmpName(), "-")
                : nullSafe(employee.getEmpName(), "-") + "（" + dept + "）";
    }

    /**
     * 解析审批人的飞书 open_id：优先手机号，其次邮箱。
     * <p>
     * 邮箱未必是员工飞书账号绑定的邮箱，而手机号通常是注册标识，故优先用手机号解析。
     */
    private String resolveApproverOpenId(Employee approver) {
        if (approver == null) {
            return null;
        }
        String mobile = approver.getPhone();
        if (mobile != null && !mobile.isBlank()) {
            String openId = cardClient.resolveOpenIdByMobile(mobile);
            if (openId != null) {
                return openId;
            }
        }
        String email = approver.getEmail();
        if (email == null || email.isBlank()) {
            return null;
        }
        return cardClient.resolveOpenIdByEmail(email);
    }

    /** 流程类型中文名；实例为空时回退为「流程」 */
    private String flowLabel(FlowInstance instance) {
        return FlowConstants.flowTypeLabel(instance == null ? null : instance.getFlowType());
    }

    private String formatDays(BigDecimal days) {
        return days == null ? "0" : days.stripTrailingZeros().toPlainString();
    }

    private String nullSafe(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
