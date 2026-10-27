package org.example.hragent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.LeaveRequestMapper;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.service.LeaveRequestService;
import org.example.hragent.vo.FlowInstanceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请假单 Service 实现
 */
@Slf4j
@Service
public class LeaveRequestServiceImpl extends BaseServiceImpl<LeaveRequestMapper, LeaveRequest>
        implements LeaveRequestService {

    private final FlowOrchestratorService flowOrchestratorService;
    private final ObjectMapper objectMapper;

    public LeaveRequestServiceImpl(FlowOrchestratorService flowOrchestratorService, ObjectMapper objectMapper) {
        this.flowOrchestratorService = flowOrchestratorService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveRequest createAndStart(Long empId, String leaveType, LocalDate startDate, LocalDate endDate, String reason) {
        BigDecimal days = BigDecimal.valueOf(ChronoUnit.DAYS.between(startDate, endDate) + 1);

        LeaveRequest request = new LeaveRequest();
        request.setEmpId(empId);
        request.setLeaveType(leaveType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setLeaveDays(days);
        request.setReason(reason);
        request.setStatus(0);
        this.save(request);

        // 发起请假审批流程：bizId=请假单ID，bizJson 平铺业务变量（用 ObjectMapper 序列化，避免手工拼串转义错误）
        FlowStartDto dto = new FlowStartDto();
        dto.setProcessKey(FlowConstants.PROC_KEY_LEAVE_REQUEST);
        dto.setBizId(request.getId());
        Map<String, Object> bizVars = new LinkedHashMap<>();
        bizVars.put("leaveType", leaveType);
        bizVars.put("startDate", startDate.toString());
        bizVars.put("endDate", endDate.toString());
        bizVars.put("leaveDays", days);
        bizVars.put("reason", reason == null ? "" : reason);
        try {
            dto.setBizJson(objectMapper.writeValueAsString(bizVars));
        } catch (Exception e) {
            log.error("请假业务变量序列化失败", e);
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "请假业务参数序列化失败");
        }
        FlowInstanceVO vo = flowOrchestratorService.start(dto);

        request.setFlowInstanceId(vo.getId());
        this.updateById(request);
        return request;
    }
}
