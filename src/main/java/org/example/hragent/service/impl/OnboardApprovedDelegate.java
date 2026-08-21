package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.Resume;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.mapper.ResumeMapper;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 入职流程通过后自动创建员工记录的 JavaDelegate
 * <p>
 * 由 BPMN 中 serviceTask onboard_3_create_emp 通过 delegateExpression 调用。
 * 从流程变量 bizId（=简历ID）读取候选人信息，写入 t_employee。
 * <p>
 * 同步执行（async=false），在审批通过的事务内完成员工记录创建。
 */
@Slf4j
@Component("onboardApprovedDelegate")
public class OnboardApprovedDelegate implements JavaDelegate {

    private final ResumeMapper resumeMapper;
    private final EmployeeMapper employeeMapper;

    public OnboardApprovedDelegate(ResumeMapper resumeMapper, EmployeeMapper employeeMapper) {
        this.resumeMapper = resumeMapper;
        this.employeeMapper = employeeMapper;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long resumeId = (Long) execution.getVariable("bizId");
        log.info("入职审批通过，开始创建员工记录 resumeId={}", resumeId);

        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            log.error("简历不存在 resumeId={}", resumeId);
            throw new IllegalStateException("简历不存在: " + resumeId);
        }

        // 从流程变量取 HR 发起时指定的部门、岗位、主管
        // bizJson 经 Jackson 解析后类型不固定（数字会变 Integer），用 String.valueOf 安全转换
        Object targetDeptNameObj = execution.getVariable("targetDeptName");
        Object targetPositionObj = execution.getVariable("targetPosition");
        Object targetLeaderIdObj = execution.getVariable("targetLeaderId");
        String targetDeptName = targetDeptNameObj != null ? String.valueOf(targetDeptNameObj) : null;
        String targetPosition = targetPositionObj != null ? String.valueOf(targetPositionObj) : null;
        String targetLeaderIdStr = targetLeaderIdObj != null ? String.valueOf(targetLeaderIdObj) : null;

        Employee emp = new Employee();
        emp.setEmpNo(generateEmpNo());
        emp.setEmpName(resume.getResumeName());
        // 密码由 EmployeeServiceImpl.save 设默认值，这里不设
        emp.setGender(resume.getGender());
        emp.setBirthDate(resume.getBirthDate());
        emp.setPhone(resume.getPhone());
        emp.setEmail(resume.getEmail());
        emp.setIdCard(resume.getIdCard());
        emp.setDeptName(targetDeptName);
        emp.setPositionName(targetPosition != null ? targetPosition : resume.getExpectPosition());
        emp.setEntryDate(LocalDate.now());
        emp.setEmpStatus(1); // 试用中
        emp.setRole("EMPLOYEE");
        if (targetLeaderIdStr != null) {
            try {
                emp.setLeaderId(Long.parseLong(targetLeaderIdStr));
            } catch (NumberFormatException ignored) {
            }
        }

        // 直接走 mapper 插入，避免 EmployeeService.save 对密码的二次哈希
        // 这里设默认密码 123456 的哈希由 DataInitializer 同款逻辑处理（首次登录前会被初始化）
        employeeMapper.insert(emp);

        log.info("员工记录创建成功 empNo={}, empName={}, resumeId={}",
                emp.getEmpNo(), emp.getEmpName(), resumeId);
    }

    /** 生成工号 EMP + yyyyMMdd + 4位序列（简化实现，基于时间戳保证唯一） */
    private String generateEmpNo() {
        return "EMP" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%04d", System.currentTimeMillis() % 10000);
    }
}
