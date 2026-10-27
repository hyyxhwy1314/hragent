package org.example.hragent.flow.delegate;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.mapper.LeaveRequestMapper;
import org.example.hragent.service.EmailService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 请假流程审批通过后自动给员工发送结果通知邮件。
 * <p>
 * 邮件节点在 BPMN 中配置 flowable:async="true"（项目规范：异步发送不阻塞主流程）；
 * 邮箱缺失或发送失败仅记录日志，不抛异常——业务状态已落库，不应因邮件失败回滚流程。
 */
@Slf4j
@Component("leaveRequestEmailDelegate")
public class LeaveRequestEmailDelegate implements JavaDelegate {

    private final LeaveRequestMapper leaveRequestMapper;
    private final EmployeeMapper employeeMapper;
    private final EmailService emailService;

    public LeaveRequestEmailDelegate(LeaveRequestMapper leaveRequestMapper,
                                     EmployeeMapper employeeMapper,
                                     EmailService emailService) {
        this.leaveRequestMapper = leaveRequestMapper;
        this.employeeMapper = employeeMapper;
        this.emailService = emailService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long bizId = (Long) execution.getVariable("bizId");
        LeaveRequest request = leaveRequestMapper.selectById(bizId);
        if (request == null) {
            log.warn("请假单不存在，跳过邮件发送 bizId={}", bizId);
            return;
        }
        Employee emp = employeeMapper.selectById(request.getEmpId());
        if (emp == null || emp.getEmail() == null || !emp.getEmail().contains("@")) {
            log.warn("员工邮箱不可用，跳过邮件发送 empId={}, bizId={}", request.getEmpId(), bizId);
            return;
        }
        emailService.sendSimpleMail(emp.getEmail(), "请假审批结果通知",
                "尊敬的 " + emp.getEmpName() + "：\n\n"
                        + "您的请假申请已审批通过，请假信息如下：\n\n"
                        + "请假类型：" + request.getLeaveType() + "\n"
                        + "请假日期：" + request.getStartDate() + " 至 " + request.getEndDate() + "\n"
                        + "请假天数：" + request.getLeaveDays() + " 天\n"
                        + (request.getReason() == null ? "" : "请假原因：" + request.getReason() + "\n") + "\n"
                        + "请合理安排工作交接，祝您假期愉快。\n\n"
                        + "此致\n人力资源部\n");
        log.info("请假审批结果邮件发送成功 bizId={}, to={}", bizId, emp.getEmail());
    }
}
