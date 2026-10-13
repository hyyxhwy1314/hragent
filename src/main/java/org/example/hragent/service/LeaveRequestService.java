package org.example.hragent.service;

import org.example.hragent.entity.LeaveRequest;

import java.time.LocalDate;

/**
 * 请假单 Service
 */
public interface LeaveRequestService extends BaseService<LeaveRequest> {

    /**
     * 创建请假单并发起审批流程（直属上级 → HR 两级审批），返回已回填流程实例ID的请假单。
     *
     * @param empId     申请员工ID
     * @param leaveType 请假类型（年假/事假/病假/调休等）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param reason    请假原因（可空）
     */
    LeaveRequest createAndStart(Long empId, String leaveType, LocalDate startDate, LocalDate endDate, String reason);
}
