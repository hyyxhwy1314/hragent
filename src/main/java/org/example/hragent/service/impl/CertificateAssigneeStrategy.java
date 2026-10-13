package org.example.hragent.service.impl;

import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.CertificateRequest;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.CertificateRequestMapper;
import org.example.hragent.mapper.EmployeeMapper;
import org.springframework.stereotype.Component;

/**
 * 证明开具流程审批人解析策略（业务单 → 申请员工 → 直属上级 + HR）
 */
@Component
public class CertificateAssigneeStrategy extends AbstractBizRequestAssigneeStrategy {

    private final CertificateRequestMapper certificateRequestMapper;

    public CertificateAssigneeStrategy(EmployeeMapper employeeMapper, CertificateRequestMapper certificateRequestMapper) {
        super(employeeMapper);
        this.certificateRequestMapper = certificateRequestMapper;
    }

    @Override
    public String supportProcessKey() {
        return FlowConstants.PROC_KEY_CERTIFICATE;
    }

    @Override
    protected Long resolveApplyEmpId(Long bizId) {
        CertificateRequest request = certificateRequestMapper.selectById(bizId);
        BusinessException.throwIf(request == null, ErrorCode.DATA_NOT_FOUND, "证明申请单不存在");
        return request.getEmpId();
    }
}
