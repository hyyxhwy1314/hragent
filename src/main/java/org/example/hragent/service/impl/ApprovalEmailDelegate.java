package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.service.EmailService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 转正 / 调岗 / 离职 流程审批通过后，自动给员工邮箱发送审批结果通知。
 * <p>
 * 由各 BPMN 的 serviceTask（${approvalEmailDelegate}）调用，根据 process key 拼装对应邮件正文。
 * 读取流程变量 bizId（=员工ID）查询员工姓名与邮箱；调岗场景额外读取
 * targetDeptName / targetPosition。
 * <p>
 * 同步执行；邮箱缺失或发送失败仅记录日志，不抛异常——业务状态已落库，不应因邮件失败回滚流程。
 */
@Slf4j
@Component("approvalEmailDelegate")
public class ApprovalEmailDelegate implements JavaDelegate {

    private final EmployeeMapper employeeMapper;
    private final EmailService emailService;

    public ApprovalEmailDelegate(EmployeeMapper employeeMapper, EmailService emailService) {
        this.employeeMapper = employeeMapper;
        this.emailService = emailService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long empId = (Long) execution.getVariable("bizId");
        // 流程类型在发起时作为 flowType 变量写入（REGULAR/TRANSFER/LEAVE）
        String flowType = execution.getVariable("flowType", String.class);
        log.info("审批结果邮件节点 flowType={}, empId={}", flowType, empId);

        Employee emp = empId != null ? employeeMapper.selectById(empId) : null;
        if (emp == null) {
            log.warn("员工不存在，跳过邮件发送 empId={}, flowType={}", empId, flowType);
            return;
        }

        // 收件人解析：以当事员工档案邮箱为主；为空时回退到发起人(applyEmpId)邮箱；再空则显式告警跳过
        String to = effectiveRecipient(emp, execution);
        if (to == null) {
            log.warn("未找到可用收件人邮箱，跳过邮件发送 empId={}, empName={}, flowType={}",
                    empId, emp.getEmpName(), flowType);
            return;
        }

        if ("REGULAR".equals(flowType)) {
            emailService.sendSimpleMail(to, "转正审批结果通知",
                    buildRegularContent(emp));
        } else if ("TRANSFER".equals(flowType)) {
            Object deptObj = execution.getVariable("targetDeptName");
            Object posObj = execution.getVariable("targetPosition");
            emailService.sendSimpleMail(to, "调岗审批结果通知",
                    buildTransferContent(emp, deptObj != null ? String.valueOf(deptObj) : null,
                            posObj != null ? String.valueOf(posObj) : null));
        } else if ("LEAVE".equals(flowType)) {
            emailService.sendSimpleMail(to, "离职审批结果通知",
                    buildLeaveContent(emp));
        } else {
            log.warn("未知流程类型，跳过邮件发送 flowType={}", flowType);
        }
    }

    /**
     * 解析收件人邮箱：
     * <ol>
     *   <li>当事员工档案邮箱（校验格式）</li>
     *   <li>为空则回退到发起人 applyEmpId 的档案邮箱</li>
     *   <li>仍无效返回 null，由调用方告警跳过</li>
     * </ol>
     * 不依赖简历关联——上级/管理员等无简历员工同样有档案邮箱。
     */
    private String effectiveRecipient(Employee emp, DelegateExecution execution) {
        if (emp.getEmail() != null && !emp.getEmail().isBlank()
                && emp.getEmail().contains("@")) {
            return emp.getEmail();
        }
        Object applyObj = execution.getVariable("applyEmpId");
        if (applyObj != null) {
            Long applyEmpId = Long.valueOf(String.valueOf(applyObj));
            if (!applyEmpId.equals(emp.getId())) {
                Employee applicant = employeeMapper.selectById(applyEmpId);
                if (applicant != null && applicant.getEmail() != null
                        && !applicant.getEmail().isBlank()
                        && applicant.getEmail().contains("@")) {
                    return applicant.getEmail();
                }
            }
        }
        return null;
    }

    private String buildRegularContent(Employee emp) {
        return "尊敬的 " + emp.getEmpName() + "：\n\n"
                + "恭喜您，您的转正申请已审批通过！您已正式成为公司正式员工。\n\n"
                + "工号：" + emp.getEmpNo() + "\n"
                + "部门：" + (emp.getDeptName() != null ? emp.getDeptName() : "-") + "\n"
                + "岗位：" + (emp.getPositionName() != null ? emp.getPositionName() : "-") + "\n\n"
                + "感谢您在试用期的努力，期待您后续的优异表现。\n\n"
                + "此致\n人力资源部\n";
    }

    private String buildTransferContent(Employee emp, String dept, String position) {
        return "尊敬的 " + emp.getEmpName() + "：\n\n"
                + "您的调岗申请已审批通过，您的岗位信息更新如下：\n\n"
                + "工号：" + emp.getEmpNo() + "\n"
                + "新部门：" + (dept != null && !dept.isBlank() ? dept : "（未指定）") + "\n"
                + "新岗位：" + (position != null && !position.isBlank() ? position : "（未指定）") + "\n\n"
                + "请与新部门负责人对接后续工作安排。\n\n"
                + "此致\n人力资源部\n";
    }

    private String buildLeaveContent(Employee emp) {
        return "尊敬的 " + emp.getEmpName() + "：\n\n"
                + "您的离职申请已审批通过，您将于即日办理离职交接手续。\n\n"
                + "工号：" + emp.getEmpNo() + "\n"
                + "请与直属上级及人力资源部确认交接安排与离职日期。\n\n"
                + "感谢您过往的付出，祝您前程似锦。\n\n"
                + "此致\n人力资源部\n";
    }
}