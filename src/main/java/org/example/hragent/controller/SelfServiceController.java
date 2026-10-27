package org.example.hragent.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.CertificateRequest;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.ExpireReminder;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.entity.LeaveBalance;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.service.CertificateRequestService;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.service.ExpireReminderService;
import org.example.hragent.service.LeaveBalanceService;
import org.example.hragent.service.LeaveRequestService;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 员工自助 Controller
 * <p>
 * 供前端「员工自助」页面调用的 REST 接口：请假申请/余额/记录、证明申请/记录、到期提醒。
 * 申请人一律取当前登录员工，无需在请求中携带。
 */
@RestController
@RequestMapping("/self-service")
public class SelfServiceController {

    private final LeaveRequestService leaveRequestService;
    private final LeaveBalanceService leaveBalanceService;
    private final CertificateRequestService certificateRequestService;
    private final ExpireReminderService expireReminderService;
    private final EmployeeService employeeService;
    private final FlowInstanceMapper flowInstanceMapper;

    public SelfServiceController(LeaveRequestService leaveRequestService,
                                 LeaveBalanceService leaveBalanceService,
                                 CertificateRequestService certificateRequestService,
                                 ExpireReminderService expireReminderService,
                                 EmployeeService employeeService,
                                 FlowInstanceMapper flowInstanceMapper) {
        this.leaveRequestService = leaveRequestService;
        this.leaveBalanceService = leaveBalanceService;
        this.certificateRequestService = certificateRequestService;
        this.expireReminderService = expireReminderService;
        this.employeeService = employeeService;
        this.flowInstanceMapper = flowInstanceMapper;
    }

    // ================= 请假 =================

    /** 发起请假申请（创建请假单并发起审批流程） */
    @PostMapping("/leave/apply")
    public R<LeaveApplyResult> applyLeave(@Valid @RequestBody LeaveApplyReq req) {
        Long empId = CurrentUserService.requireEmpId();
        BusinessException.throwIf(req.getEndDate().isBefore(req.getStartDate()), ErrorCode.PARAM_ERROR, "请假结束日期不能早于开始日期");

        LeaveRequest request = leaveRequestService.createAndStart(empId, req.getLeaveType(), req.getStartDate(), req.getEndDate(), req.getReason());

        // 余额校验（不足仅提示，不阻断发起）
        int year = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceService.getOrInit(empId, year);
        BigDecimal used = balance.getUsedDays() == null ? BigDecimal.ZERO : balance.getUsedDays();
        BigDecimal remaining = balance.getTotalDays().subtract(used);

        LeaveApplyResult result = new LeaveApplyResult();
        result.setRequestId(request.getId());
        result.setFlowNo(flowNoOf(request.getFlowInstanceId()));
        result.setLeaveDays(request.getLeaveDays());
        result.setBalanceTip(remaining.compareTo(request.getLeaveDays()) < 0
                ? String.format("假期余额剩余 %s 天，不足以覆盖本次 %s 天，审批通过后可能扣减失败", remaining, request.getLeaveDays())
                : "");
        return R.ok(result);
    }

    /** 查询当前员工本年度假期余额 */
    @GetMapping("/leave/balance")
    public R<LeaveBalanceVO> leaveBalance() {
        Long empId = CurrentUserService.requireEmpId();
        int year = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceService.getOrInit(empId, year);
        Employee emp = employeeService.getById(empId);
        BigDecimal used = balance.getUsedDays() == null ? BigDecimal.ZERO : balance.getUsedDays();

        LeaveBalanceVO vo = new LeaveBalanceVO();
        vo.setEmpId(empId);
        vo.setEmpName(emp == null ? "" : emp.getEmpName());
        vo.setYear(year);
        vo.setTotalDays(balance.getTotalDays());
        vo.setUsedDays(used);
        vo.setRemainingDays(balance.getTotalDays().subtract(used));
        return R.ok(vo);
    }

    /** 查询当前员工请假记录（含审批状态与流程单号） */
    @GetMapping("/leave/records")
    public R<List<LeaveRequestVO>> leaveRecords() {
        Long empId = CurrentUserService.requireEmpId();
        List<LeaveRequest> list = leaveRequestService.lambdaQuery()
                .eq(LeaveRequest::getEmpId, empId)
                .orderByDesc(LeaveRequest::getId)
                .last("LIMIT 50")
                .list();
        // 批量取流程实例，避免逐条 selectById（N+1）
        Map<Long, FlowInstance> instMap = loadFlowInstances(
                list.stream().map(LeaveRequest::getFlowInstanceId).collect(Collectors.toList()));
        return R.ok(list.stream().map(r -> {
            LeaveRequestVO vo = new LeaveRequestVO();
            vo.setId(r.getId());
            vo.setLeaveType(r.getLeaveType());
            vo.setStartDate(r.getStartDate());
            vo.setEndDate(r.getEndDate());
            vo.setLeaveDays(r.getLeaveDays());
            vo.setReason(r.getReason());
            vo.setCreateTime(r.getCreateTime());
            fillFlow(vo, instMap.get(r.getFlowInstanceId()), r.getStatus());
            return vo;
        }).collect(Collectors.toList()));
    }

    // ================= 证明开具 =================

    /** 发起证明开具申请（在职/收入证明） */
    @PostMapping("/certificate/apply")
    public R<CertificateApplyResult> applyCertificate(@Valid @RequestBody CertificateApplyReq req) {
        Long empId = CurrentUserService.requireEmpId();
        CertificateRequest request = certificateRequestService.createAndStart(empId, req.getCertType(), req.getPurpose());

        CertificateApplyResult result = new CertificateApplyResult();
        result.setRequestId(request.getId());
        result.setFlowNo(flowNoOf(request.getFlowInstanceId()));
        result.setCertType(request.getCertType());
        return R.ok(result);
    }

    /** 查询当前员工证明申请记录 */
    @GetMapping("/certificate/records")
    public R<List<CertificateRequestVO>> certificateRecords() {
        Long empId = CurrentUserService.requireEmpId();
        List<CertificateRequest> list = certificateRequestService.lambdaQuery()
                .eq(CertificateRequest::getEmpId, empId)
                .orderByDesc(CertificateRequest::getId)
                .last("LIMIT 50")
                .list();
        // 批量取流程实例，避免逐条 selectById（N+1）
        Map<Long, FlowInstance> instMap = loadFlowInstances(
                list.stream().map(CertificateRequest::getFlowInstanceId).collect(Collectors.toList()));
        return R.ok(list.stream().map(r -> {
            CertificateRequestVO vo = new CertificateRequestVO();
            vo.setId(r.getId());
            vo.setCertType(r.getCertType());
            vo.setPurpose(r.getPurpose());
            vo.setContent(r.getContent());
            vo.setCreateTime(r.getCreateTime());
            fillFlow(vo, instMap.get(r.getFlowInstanceId()), r.getStatus());
            return vo;
        }).collect(Collectors.toList()));
    }

    // ================= 到期提醒 =================

    /** 查询当前员工合同/转正到期提醒 */
    @GetMapping("/reminders")
    public R<List<ExpireReminderVO>> reminders() {
        Long empId = CurrentUserService.requireEmpId();
        List<ExpireReminder> list = expireReminderService.lambdaQuery()
                .eq(ExpireReminder::getEmpId, empId)
                .orderByDesc(ExpireReminder::getExpireDate)
                .last("LIMIT 20")
                .list();
        return R.ok(list.stream().map(r -> {
            ExpireReminderVO vo = new ExpireReminderVO();
            vo.setId(r.getId());
            vo.setRemindType(r.getRemindType());
            vo.setRemindTypeText("CONTRACT".equals(r.getRemindType()) ? "合同到期" : "转正到期");
            vo.setExpireDate(r.getExpireDate());
            vo.setRemindDate(r.getRemindDate());
            vo.setSent(r.getSent());
            return vo;
        }).collect(Collectors.toList()));
    }

    // ================= 辅助 =================

    private String flowNoOf(Long flowInstanceId) {
        if (flowInstanceId == null) {
            return "";
        }
        FlowInstance fi = flowInstanceMapper.selectById(flowInstanceId);
        return fi == null ? "" : fi.getFlowNo();
    }

    /** 批量加载流程实例：ID 列表 → (ID, 实例) Map，避免逐条查询（N+1） */
    private Map<Long, FlowInstance> loadFlowInstances(List<Long> ids) {
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return new java.util.HashMap<>();
        }
        return flowInstanceMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(FlowInstance::getId, f -> f, (a, b) -> a));
    }

    /** 回填流程单号/状态/状态文本；无流程实例时按申请单自身状态兜底 */
    private void fillFlow(AbstractRequestVO vo, FlowInstance fi, Integer requestStatus) {
        if (fi != null) {
            vo.setFlowNo(fi.getFlowNo());
            vo.setFlowStatus(fi.getFlowStatus());
            vo.setStatusText(FlowConstants.statusText(fi.getFlowStatus()));
            return;
        }
        vo.setStatusText(requestStatus != null && requestStatus == 1 ? "已通过" : "审批中");
    }

    // ================= 请求/响应模型 =================

    /** 请假发起请求 */
    @Data
    public static class LeaveApplyReq {
        @NotBlank(message = "请假类型不能为空")
        private String leaveType;
        @NotNull(message = "开始日期不能为空")
        private LocalDate startDate;
        @NotNull(message = "结束日期不能为空")
        private LocalDate endDate;
        private String reason;
    }

    /** 请假发起结果 */
    @Data
    public static class LeaveApplyResult {
        private Long requestId;
        private String flowNo;
        private BigDecimal leaveDays;
        private String balanceTip;
    }

    /** 假期余额视图 */
    @Data
    public static class LeaveBalanceVO {
        private Long empId;
        private String empName;
        private Integer year;
        private BigDecimal totalDays;
        private BigDecimal usedDays;
        private BigDecimal remainingDays;
    }

    /** 证明发起请求 */
    @Data
    public static class CertificateApplyReq {
        @NotBlank(message = "证明类型不能为空")
        private String certType;
        private String purpose;
    }

    /** 证明发起结果 */
    @Data
    public static class CertificateApplyResult {
        private Long requestId;
        private String flowNo;
        private String certType;
    }

    /** 申请记录公共字段（流程单号/状态） */
    @Data
    public abstract static class AbstractRequestVO {
        private String flowNo;
        private Integer flowStatus;
        private String statusText;
    }

    /** 请假记录视图 */
    @Data
    public static class LeaveRequestVO extends AbstractRequestVO {
        private Long id;
        private String leaveType;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal leaveDays;
        private String reason;
        private LocalDateTime createTime;
    }

    /** 证明申请记录视图 */
    @Data
    public static class CertificateRequestVO extends AbstractRequestVO {
        private Long id;
        private String certType;
        private String purpose;
        private String content;
        private LocalDateTime createTime;
    }

    /** 到期提醒视图 */
    @Data
    public static class ExpireReminderVO {
        private Long id;
        private String remindType;
        private String remindTypeText;
        private LocalDate expireDate;
        private LocalDate remindDate;
        private Integer sent;
    }
}
