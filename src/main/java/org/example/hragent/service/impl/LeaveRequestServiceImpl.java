package org.example.hragent.service.impl;

import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.flow.support.BizFlowStarter;
import org.example.hragent.mapper.LeaveRequestMapper;
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
@Service
public class LeaveRequestServiceImpl extends BaseServiceImpl<LeaveRequestMapper, LeaveRequest>
        implements LeaveRequestService {

    private final BizFlowStarter bizFlowStarter;

    public LeaveRequestServiceImpl(BizFlowStarter bizFlowStarter) {
        this.bizFlowStarter = bizFlowStarter;
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

        // 发起请假审批流程：bizId=请假单ID，bizJson 平铺业务变量
        Map<String, Object> bizVars = new LinkedHashMap<>();
        bizVars.put("leaveType", leaveType);
        bizVars.put("startDate", startDate.toString());
        bizVars.put("endDate", endDate.toString());
        bizVars.put("leaveDays", days);
        bizVars.put("reason", reason == null ? "" : reason);
        FlowInstanceVO vo = bizFlowStarter.start(
                FlowConstants.PROC_KEY_LEAVE_REQUEST, request.getId(), bizVars, "请假");

        request.setFlowInstanceId(vo.getId());
        this.updateById(request);
        return request;
    }
}
