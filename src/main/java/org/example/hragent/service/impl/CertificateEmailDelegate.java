package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.CertificateRequest;
import org.example.hragent.entity.Employee;
import org.example.hragent.mapper.CertificateRequestMapper;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.service.EmailService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 证明开具流程审批通过后自动将证明内容邮件发送给员工。
 * <p>
 * 邮件节点在 BPMN 中配置 flowable:async="true"；邮箱缺失或发送失败仅记录日志，不抛异常。
 */
@Slf4j
@Component("certificateEmailDelegate")
public class CertificateEmailDelegate implements JavaDelegate {

    private final CertificateRequestMapper certificateRequestMapper;
    private final EmployeeMapper employeeMapper;
    private final EmailService emailService;

    public CertificateEmailDelegate(CertificateRequestMapper certificateRequestMapper,
                                    EmployeeMapper employeeMapper,
                                    EmailService emailService) {
        this.certificateRequestMapper = certificateRequestMapper;
        this.employeeMapper = employeeMapper;
        this.emailService = emailService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long bizId = (Long) execution.getVariable("bizId");
        CertificateRequest request = certificateRequestMapper.selectById(bizId);
        if (request == null) {
            log.warn("证明申请单不存在，跳过邮件发送 bizId={}", bizId);
            return;
        }
        Employee emp = employeeMapper.selectById(request.getEmpId());
        if (emp == null || emp.getEmail() == null || !emp.getEmail().contains("@")) {
            log.warn("员工邮箱不可用，跳过邮件发送 empId={}, bizId={}", request.getEmpId(), bizId);
            return;
        }
        String content = request.getContent() != null ? request.getContent()
                : (execution.getVariable("certificateContent", String.class) != null
                ? execution.getVariable("certificateContent", String.class) : "");
        emailService.sendSimpleMail(emp.getEmail(),
                "证明开具结果通知（" + request.getCertType() + "）", content);
        log.info("证明邮件发送成功 bizId={}, to={}", bizId, emp.getEmail());
    }
}
