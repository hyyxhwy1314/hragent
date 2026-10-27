package org.example.hragent.service.impl;

import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.CertificateRequest;
import org.example.hragent.flow.support.BizFlowStarter;
import org.example.hragent.mapper.CertificateRequestMapper;
import org.example.hragent.service.CertificateRequestService;
import org.example.hragent.vo.FlowInstanceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 证明申请 Service 实现
 */
@Service
public class CertificateRequestServiceImpl extends BaseServiceImpl<CertificateRequestMapper, CertificateRequest>
        implements CertificateRequestService {

    private final BizFlowStarter bizFlowStarter;

    public CertificateRequestServiceImpl(BizFlowStarter bizFlowStarter) {
        this.bizFlowStarter = bizFlowStarter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateRequest createAndStart(Long empId, String certType, String purpose) {
        CertificateRequest request = new CertificateRequest();
        request.setEmpId(empId);
        request.setCertType(certType);
        request.setPurpose(purpose);
        request.setStatus(0);
        this.save(request);

        // 发起证明审批流程：bizId=申请单ID，bizJson 平铺业务变量
        Map<String, Object> bizVars = new LinkedHashMap<>();
        bizVars.put("certType", certType);
        bizVars.put("purpose", purpose == null ? "" : purpose);
        FlowInstanceVO vo = bizFlowStarter.start(
                FlowConstants.PROC_KEY_CERTIFICATE, request.getId(), bizVars, "证明");

        request.setFlowInstanceId(vo.getId());
        this.updateById(request);
        return request;
    }
}
