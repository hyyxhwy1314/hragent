package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.Resume;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.mapper.ResumeMapper;
import org.example.hragent.service.EmailService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final EmployeeMapper employeeMapper;
    private final EmailService emailService;

    public OnboardEmailDelegate(ResumeMapper resumeMapper, EmployeeMapper employeeMapper,
                                EmailService emailService) {
        this.resumeMapper = resumeMapper;
        this.employeeMapper = employeeMapper;
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

        // 收件人：优先用刚回填的员工主数据邮箱（入职时从简历写入 t_employee.email）
        // 员工未查到/邮箱无效时，退回从简历识别（QQ 优先，否则简历邮箱字段）
        String email = employeeEmailByEmpNo(empNo);
        if (email == null) {
            email = resolveRecipient(resume);
        }
        if (email == null || email.isBlank()) {
            log.warn("未识别到可用的简历邮箱，跳过邮件发送 resumeId={}", resumeId);
            return;
        }

        String empName = resume.getResumeName();
        String position = resume.getExpectPosition();
        String entryDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));

        String subject = "入职欢迎信 - " + (empName != null ? empName : "新同事");
        String content = buildContent(empName, empNo, position, entryDate);

        log.info("入职通知邮件收件人 email={}, resumeId={}", email, resumeId);
        emailService.sendSimpleMail(email, subject, content);
    }

    /** QQ 邮箱正则：匹配 `xxx@qq.com`（不区分大小写） */
    private static final Pattern QQ_EMAIL = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._%+-]*@qq\\.com", Pattern.CASE_INSENSITIVE);

    /** 按工号查询刚创建员工回填的主数据邮箱（无效或缺失返回 null） */
    private String employeeEmailByEmpNo(String empNo) {
        if (empNo == null || empNo.isBlank() || "（待分配）".equals(empNo)) {
            return null;
        }
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getEmpNo, empNo)
                .last("LIMIT 1"));
        if (emp == null || emp.getEmail() == null || !emp.getEmail().contains("@")) {
            return null;
        }
        return emp.getEmail();
    }

    /**
     * 解析收件人（兜底，仅当员工主数据邮箱不可用时）：
     * <ol>
     *   <li>从简历原始文本（resumeContent）识别 QQ 邮箱</li>
     *   <li>未命中则尝试 AI 结构化文本（resumeStructJson）</li>
     *   <li>仍未命中则退回简历邮箱字段（email）</li>
     * </ol>
     */
    private String resolveRecipient(Resume resume) {
        String qq = qqEmailIn(resume.getResumeContent());
        if (qq == null && resume.getResumeStructJson() != null) {
            qq = qqEmailIn(resume.getResumeStructJson());
        }
        return qq != null ? qq : resume.getEmail();
    }

    private String qqEmailIn(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = QQ_EMAIL.matcher(text);
        return m.find() ? m.group() : null;
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
