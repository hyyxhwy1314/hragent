package org.example.hragent.service.impl;

import org.example.hragent.constant.FlowConstants;
import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.mapper.LeaveRequestMapper;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.service.LeaveRequestService;
import org.example.hragent.vo.FlowInstanceVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 请假单 Service 实现
 */
@Service
public class LeaveRequestServiceImpl extends BaseServiceImpl<LeaveRequestMapper, LeaveRequest>
        implements LeaveRequestService {

    private final FlowOrchestratorService flowOrchestratorService;

    public LeaveRequestServiceImpl(FlowOrchestratorService flowOrchestratorService) {
        this.flowOrchestratorService = flowOrchestratorService;
    }

    @Override
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

        // 发起请假审批流程：bizId=请假单ID，bizJson 平铺业务变量
        FlowStartDto dto = new FlowStartDto();
        dto.setProcessKey(FlowConstants.PROC_KEY_LEAVE_REQUEST);
        dto.setBizId(request.getId());
        dto.setBizJson(String.format(
                "{\"leaveType\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"leaveDays\":%s,\"reason\":\"%s\"}",
                leaveType.replace("\"", "\\\""), startDate, endDate, days,
                reason == null ? "" : reason.replace("\"", "\\\"")));
        FlowInstanceVO vo = flowOrchestratorService.start(dto);

        request.setFlowInstanceId(vo.getId());
        this.updateById(request);
        return request;
    }
}
