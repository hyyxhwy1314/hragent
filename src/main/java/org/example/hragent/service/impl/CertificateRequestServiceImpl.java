package org.example.hragent.service.impl;

import org.example.hragent.constant.FlowConstants;
import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.entity.CertificateRequest;
import org.example.hragent.mapper.CertificateRequestMapper;
import org.example.hragent.service.CertificateRequestService;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.vo.FlowInstanceVO;
import org.springframework.stereotype.Service;

/**
 * 证明申请 Service 实现
 */
@Service
public class CertificateRequestServiceImpl extends BaseServiceImpl<CertificateRequestMapper, CertificateRequest>
        implements CertificateRequestService {

    private final FlowOrchestratorService flowOrchestratorService;

    public CertificateRequestServiceImpl(FlowOrchestratorService flowOrchestratorService) {
        this.flowOrchestratorService = flowOrchestratorService;
    }

    @Override
    public CertificateRequest createAndStart(Long empId, String certType, String purpose) {
        CertificateRequest request = new CertificateRequest();
        request.setEmpId(empId);
        request.setCertType(certType);
        request.setPurpose(purpose);
        request.setStatus(0);
        this.save(request);

        // 发起证明审批流程：bizId=申请单ID，bizJson 平铺业务变量
        FlowStartDto dto = new FlowStartDto();
        dto.setProcessKey(FlowConstants.PROC_KEY_CERTIFICATE);
        dto.setBizId(request.getId());
        dto.setBizJson(String.format("{\"certType\":\"%s\",\"purpose\":\"%s\"}",
                certType, purpose == null ? "" : purpose.replace("\"", "\\\"")));
        FlowInstanceVO vo = flowOrchestratorService.start(dto);

        request.setFlowInstanceId(vo.getId());
        this.updateById(request);
        return request;
    }
}
