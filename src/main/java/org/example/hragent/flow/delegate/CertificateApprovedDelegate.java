package org.example.hragent.flow.delegate;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.CertificateRequest;
import org.example.hragent.entity.Employee;
import org.example.hragent.mapper.CertificateRequestMapper;
import org.example.hragent.mapper.EmployeeMapper;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 证明开具流程审批通过后：生成证明内容并回写证明申请单。
 * <p>
 * 由 BPMN 中 serviceTask 通过 delegateExpression 调用，同步执行；
 * 生成的证明内容写入 t_certificate_request.content 并设为流程变量，供邮件节点使用。
 */
@Slf4j
@Component("certificateApprovedDelegate")
public class CertificateApprovedDelegate implements JavaDelegate {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CertificateRequestMapper certificateRequestMapper;
    private final EmployeeMapper employeeMapper;

    public CertificateApprovedDelegate(CertificateRequestMapper certificateRequestMapper,
                                       EmployeeMapper employeeMapper) {
        this.certificateRequestMapper = certificateRequestMapper;
        this.employeeMapper = employeeMapper;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long bizId = (Long) execution.getVariable("bizId");
        log.info("证明审批通过，开始生成证明内容 bizId={}", bizId);

        CertificateRequest request = certificateRequestMapper.selectById(bizId);
        if (request == null) {
            log.error("证明申请单不存在 bizId={}", bizId);
            throw new IllegalStateException("证明申请单不存在: " + bizId);
        }
        Employee emp = employeeMapper.selectById(request.getEmpId());
        if (emp == null) {
            log.error("员工不存在 empId={}, bizId={}", request.getEmpId(), bizId);
            throw new IllegalStateException("员工不存在: " + request.getEmpId());
        }

        String content = buildContent(request, emp);
        request.setContent(content);
        request.setStatus(1); // 通过
        certificateRequestMapper.updateById(request);

        // 证明内容写入流程变量，供邮件节点组装正文
        execution.setVariable("certificateContent", content);
        log.info("证明内容生成成功 bizId={}, certType={}, empId={}", bizId, request.getCertType(), request.getEmpId());
    }

    /** 按证明类型生成证明文本（在职证明 / 收入证明） */
    private String buildContent(CertificateRequest request, Employee emp) {
        String today = LocalDate.now().format(FMT);
        String purpose = request.getPurpose() != null ? request.getPurpose() : "";
        String base = "证 明\n\n"
                + "兹证明 " + emp.getEmpName()
                + (emp.getIdCard() != null && !emp.getIdCard().isBlank()
                ? "（身份证号：" + emp.getIdCard() + "）" : "")
                + " 系我公司员工，自 " + (emp.getEntryDate() != null ? emp.getEntryDate().format(FMT) : "-")
                + " 入职，现任 " + nvl(emp.getDeptName()) + " " + nvl(emp.getPositionName()) + "。\n\n";
        if ("收入证明".equals(request.getCertType())) {
            base += "该员工月收入（税前）为 " + nvl(emp.getBaseSalary()) + " 元。\n\n";
        }
        if (!purpose.isBlank()) {
            base += "本证明用于：" + purpose + "。\n\n";
        }
        base += "特此证明。\n\n公司人力资源部\n" + today;
        return base;
    }

    private String nvl(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
