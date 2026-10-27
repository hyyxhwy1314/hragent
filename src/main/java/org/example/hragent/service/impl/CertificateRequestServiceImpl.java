package org.example.hragent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.entity.CertificateRequest;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.CertificateRequestMapper;
import org.example.hragent.service.CertificateRequestService;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.vo.FlowInstanceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 证明申请 Service 实现
 */
@Slf4j
@Service
public class CertificateRequestServiceImpl extends BaseServiceImpl<CertificateRequestMapper, CertificateRequest>
        implements CertificateRequestService {

    private final FlowOrchestratorService flowOrchestratorService;
    private final ObjectMapper objectMapper;

    public CertificateRequestServiceImpl(FlowOrchestratorService flowOrchestratorService, ObjectMapper objectMapper) {
        this.flowOrchestratorService = flowOrchestratorService;
        this.objectMapper = objectMapper;
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

        // 发起证明审批流程：bizId=申请单ID，bizJson 平铺业务变量（用 ObjectMapper 序列化，避免手工拼串转义错误）
        FlowStartDto dto = new FlowStartDto();
        dto.setProcessKey(FlowConstants.PROC_KEY_CERTIFICATE);
        dto.setBizId(request.getId());
        Map<String, Object> bizVars = new LinkedHashMap<>();
        bizVars.put("certType", certType);
        bizVars.put("purpose", purpose == null ? "" : purpose);
        try {
            dto.setBizJson(objectMapper.writeValueAsString(bizVars));
        } catch (Exception e) {
            log.error("证明业务变量序列化失败", e);
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "证明业务参数序列化失败");
        }
        FlowInstanceVO vo = flowOrchestratorService.start(dto);

        request.setFlowInstanceId(vo.getId());
        this.updateById(request);
        return request;
    }
}
