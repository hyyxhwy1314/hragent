package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Resume;
import org.example.hragent.mapper.ResumeMapper;
import org.example.hragent.service.EmailService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 入职流程通过、员工记录创建后，自动发送入职通知邮件给员工邮箱。
 * <p>
 * 由 BPMN 中 serviceTask onboard_4_send_email 通过 delegateExpression 调用。
 * 读取流程变量 bizId（=简历ID）查询候选人邮箱，以及 OnboardApprovedDelegate
 * 写入的 onboardEmpNo（工号），组装入职通知邮件正文。
 * <p>
 * 同步执行；发送失败仅记录错误日志，不抛异常——员工记录已创建，不应因邮件失败回滚流程。
 */
@Slf4j
@Component("onboardEmailDelegate")
public class OnboardEmailDelegate implements JavaDelegate {

    private final ResumeMapper resumeMapper;
    private final EmailService emailService;

    public OnboardEmailDelegate(ResumeMapper resumeMapper, EmailService emailService) {
        this.resumeMapper = resumeMapper;
        this.emailService = emailService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long resumeId = (Long) execution.getVariable("bizId");
        log.info("入职流程进入邮件通知节点 resumeId={}", resumeId);

        Resume resume = resumeId != null ? resumeMapper.selectById(resumeId) : null;
        if (resume == null) {
            log.warn("简历不存在，跳过邮件发送 resumeId={}", resumeId);
            return;
        }

        // 从流程变量取工号（OnboardApprovedDelegate 创建员工后写入）
        Object empNoObj = execution.getVariable("onboardEmpNo");
        String empNo = empNoObj != null ? String.valueOf(empNoObj) : "（待分配）";

        String empName = resume.getResumeName();
        String email = resume.getEmail();
        String position = resume.getExpectPosition();
        String entryDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));

        String subject = "入职欢迎信 - " + (empName != null ? empName : "新同事");
        String content = buildContent(empName, empNo, position, entryDate);

        emailService.sendSimpleMail(email, subject, content);
    }

    private String buildContent(String empName, String empNo, String position, String entryDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("尊敬的 ").append(empName != null ? empName : "新同事").append("：\n\n");
        sb.append("欢迎加入我们！您的入职已审批通过，以下是您的入职信息：\n\n");
        sb.append("工号：").append(empNo).append("\n");
        sb.append("岗位：").append(position != null ? position : "（待沟通确认）").append("\n");
        sb.append("入职日期：").append(entryDate).append("\n\n");
        sb.append("初始登录密码为 123456，请在首次登录后及时修改。\n");
        sb.append("如需帮助，请联系人力资源部。\n\n");
        sb.append("此致\n").append("人力资源部\n");
        return sb.toString();
    }
}
