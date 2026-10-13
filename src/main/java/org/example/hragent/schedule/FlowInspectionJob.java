package org.example.hragent.schedule;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.service.EmailService;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.service.FlowInspectionService;
import org.example.hragent.vo.StuckFlowVO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 流程异常巡检定时任务：每日扫描长时间未审批、异常卡住的进行中流程，自动告警。
 * <p>
 * 判定阈值默认 48 小时；命中后向当前处理人发送提醒邮件（邮箱缺失或发送失败仅记录日志，不阻断）。
 * 扫描采用游标分页，避免全表加载造成长事务与锁等待。
 */
@Slf4j
@Component
public class FlowInspectionJob {

    /** 超时阈值（小时）：当前节点停留超过该时长视为异常卡住 */
    private static final int STUCK_THRESHOLD_HOURS = 48;

    private final FlowInspectionService flowInspectionService;
    private final EmployeeService employeeService;
    private final EmailService emailService;

    public FlowInspectionJob(FlowInspectionService flowInspectionService,
                             EmployeeService employeeService,
                             EmailService emailService) {
        this.flowInspectionService = flowInspectionService;
        this.employeeService = employeeService;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void inspectStuckFlows() {
        List<StuckFlowVO> stuckFlows = flowInspectionService.scanStuckFlows(STUCK_THRESHOLD_HOURS);
        if (stuckFlows.isEmpty()) {
            log.info("流程异常巡检完成：未发现超时未审批流程（阈值 {} 小时）", STUCK_THRESHOLD_HOURS);
            return;
        }
        log.warn("流程异常巡检发现 {} 个超时未审批流程（阈值 {} 小时）", stuckFlows.size(), STUCK_THRESHOLD_HOURS);
        stuckFlows.forEach(this::alert);
    }

    /** 告警：记录日志并向当前处理人发送提醒邮件 */
    private void alert(StuckFlowVO vo) {
        log.warn("流程异常卡住：单号={}, 类型={}, 节点={}, 处理人={}, 已停留={}小时",
                vo.getFlowNo(), vo.getFlowType(), vo.getCurrentNode(),
                vo.getAssigneeName(), vo.getPendingHours());
        if (vo.getAssigneeEmpId() == null) {
            return;
        }
        Employee assignee = employeeService.getById(vo.getAssigneeEmpId());
        if (assignee == null || assignee.getEmail() == null || !assignee.getEmail().contains("@")) {
            log.warn("处理人邮箱不可用，跳过异常流程告警邮件 flowNo={}", vo.getFlowNo());
            return;
        }
        emailService.sendSimpleMail(assignee.getEmail(), "待办审批超时提醒",
                String.format("您好，流程单号 %s（%s）已停留在「%s」节点超过 %d 小时，请及时处理。",
                        vo.getFlowNo(), vo.getFlowType(), vo.getCurrentNode(), vo.getPendingHours()));
    }
}