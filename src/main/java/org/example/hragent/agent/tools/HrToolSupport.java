package org.example.hragent.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.entity.JobPost;
import org.example.hragent.entity.LeaveRequest;
import org.example.hragent.entity.Performance;
import org.example.hragent.entity.Resume;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.service.ResumeService;
import org.example.hragent.utils.CurrentUserService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HR 工具公共支持：集中承载各业务工具类共用的查询解析、文本格式化与实体查找逻辑。
 */
@Component
public class HrToolSupport {

    /** 员工状态：在职 */
    public static final int STATUS_ACTIVE = 1;
    /** 员工状态：试用 */
    public static final int STATUS_PROBATION = 2;
    /** 简历状态：录用（发起入职的前置状态） */
    public static final int RESUME_STATUS_ACCEPTED = 3;

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d{1,18}");
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("[0-9]{3,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern APPLICANT_PREFIX_PATTERN = Pattern.compile(
            "(?:为|给|替|帮)\\s*([\\u4e00-\\u9fa5]{2,4})(?=\\s*(?:发起|申请|查询|办理|开))");
    private static final Pattern APPLICANT_OWNER_PATTERN = Pattern.compile(
            "(?:查询|办理|发起|申请)\\s*([\\u4e00-\\u9fa5]{2,4})\\s*的(?:假期|请假|余额|记录|证明)");
    private static final Pattern APPLICANT_CHECK_PATTERN = Pattern.compile(
            "(?:校验|检查|审核)\\s*([\\u4e00-\\u9fa5]{2,4})\\s*(?:是否|能否|可以|符合|满足|的)");
    private static final Pattern TARGET_LEADER_PATTERN = Pattern.compile("(主管|经理|审批人|leader)(?:是|为|：|:|,)");

    private final EmployeeService employeeService;
    private final ResumeService resumeService;
    private final FlowInstanceMapper flowInstanceMapper;

    public HrToolSupport(EmployeeService employeeService,
                         ResumeService resumeService,
                         FlowInstanceMapper flowInstanceMapper) {
        this.employeeService = employeeService;
        this.resumeService = resumeService;
        this.flowInstanceMapper = flowInstanceMapper;
    }

    // ==================== 通用文本 / 字段提取 ====================

    public String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    /** 从查询文本中提取搜索词：优先数字（潜在 ID），否则取最长词 */
    public String extractSearchTerm(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        String[] words = query.split("\\s+");
        for (String word : words) {
            if (word.matches("\\d+")) {
                return word;
            }
        }
        String longest = "";
        for (String word : words) {
            if (word.length() > longest.length()) {
                longest = word;
            }
        }
        return longest.isEmpty() ? query : longest;
    }

    /** 提取指定字段后的内容（如 "姓名李四" → "李四"） */
    public String extractField(String query, String key) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        Matcher m = Pattern.compile(key + "\\s*(?:是|为|：|:|,|，)?\\s*([^\\s，,；;]+)").matcher(query);
        return m.find() ? m.group(1) : null;
    }

    /** 提取非空字段（未匹配返回 null） */
    public String extractNonEmpty(String query, String key) {
        String v = extractField(query, key);
        return v == null || v.isEmpty() ? null : v;
    }

    public String extractPhone(String query) {
        return query == null ? null : firstMatch(PHONE_PATTERN, query);
    }

    public String extractEmail(String query) {
        return query == null ? null : firstMatch(EMAIL_PATTERN, query);
    }

    /** 从查询中提取数字 ID（用于流程实例ID、简历ID等） */
    public Long extractLong(String query) {
        if (query == null) {
            return null;
        }
        String matched = firstMatch(NUMBER_PATTERN, query);
        if (matched == null) {
            return null;
        }
        try {
            return Long.valueOf(matched);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从审批查询中提取任务ID（Flowable 任务ID一般为纯数字） */
    public String extractTaskId(String query) {
        if (query == null) {
            return null;
        }
        return firstMatch(TASK_ID_PATTERN, query.replaceFirst("^(流程|实例|业务)\\s*\\d+", ""));
    }

    /** 从审批查询中提取审批结果：true=通过，false=拒绝，无法识别返回 null */
    public Boolean extractApproved(String query) {
        if (query == null) {
            return null;
        }
        if (query.matches(".*(拒绝|驳回|不同意|reject|refuse|no).*")) {
            return Boolean.FALSE;
        }
        if (query.matches(".*(通过|同意|批准|approve|yes).*")) {
            return Boolean.TRUE;
        }
        return null;
    }

    /** 从审批查询中提取审批意见（"意见/备注/comment" 后的内容） */
    public String extractComment(String query) {
        if (query == null) {
            return null;
        }
        Matcher m = Pattern.compile("(意见|备注|comment)\\s*(?:是|为|：|:|,|，)?\\s*(.+)$").matcher(query);
        return m.find() ? m.group(2).trim() : null;
    }

    /** 提取查询中第 occurrence 个日期（格式 yyyy-MM-dd） */
    public LocalDate extractDate(String query, int occurrence) {
        if (query == null) {
            return null;
        }
        Matcher m = DATE_PATTERN.matcher(query);
        int idx = 0;
        while (m.find()) {
            if (++idx == occurrence) {
                try {
                    return LocalDate.parse(m.group(0), DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 从自助类查询中提取员工姓名（如"为张三发起请假" → "张三"） */
    public String extractSelfServiceEmpName(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (Pattern pattern : List.of(APPLICANT_PREFIX_PATTERN, APPLICANT_OWNER_PATTERN, APPLICANT_CHECK_PATTERN)) {
            Matcher m = pattern.matcher(query);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    /** 从流程类查询中提取员工姓名（如"发起张三的离职流程" → "张三"） */
    public String extractProcessEmployeeName(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        String name = query.replaceAll("^(发起|为)", "")
                .replaceAll("的(离职|入职|调岗|转正)流程$", "")
                .trim();
        return name.isEmpty() ? null : name;
    }

    /**
     * 从入职发起查询中提取候选人（姓名或数字简历ID）。
     * 如"为候选人李四发起入职" → "李四"；"简历12发起入职" → "12"。
     */
    public String extractProcessCandidate(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        String c = query.replaceAll("^(为|给)?候选人?\\s*", "")
                .replaceAll("发起入职(流程)?(，|,|$).*", "")
                .replaceAll("简历\\s*", "")
                .trim();
        return c.isEmpty() ? null : c;
    }

    /**
     * 从入职发起查询中提取用人部门主管姓名。
     * 如"...用人部门主管是王五" → "王五"；"...主管：王五" → "王五"。
     */
    public String extractTargetLeader(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        Matcher m = TARGET_LEADER_PATTERN.matcher(query);
        if (!m.find()) {
            return null;
        }
        String rest = query.substring(m.end()).trim();
        int cut = rest.length();
        for (String sep : new String[]{"，", ",", "；", ";", "。", " "}) {
            int idx = rest.indexOf(sep);
            if (idx >= 0) {
                cut = Math.min(cut, idx);
            }
        }
        String name = rest.substring(0, cut).trim();
        return name.isEmpty() ? null : name;
    }

    // ==================== 分类 / 状态文本 ====================

    public String classifyFlowType(String query) {
        if (query == null) {
            return null;
        }
        if (query.matches(".*(入职).*")) return FlowConstants.FLOW_TYPE_ONBOARD;
        if (query.matches(".*(离职).*")) return FlowConstants.FLOW_TYPE_LEAVE;
        if (query.matches(".*(调岗).*")) return FlowConstants.FLOW_TYPE_TRANSFER;
        if (query.matches(".*(转正).*")) return FlowConstants.FLOW_TYPE_REGULAR;
        return null;
    }

    public Integer classifyFlowStatus(String query) {
        if (query == null) {
            return null;
        }
        if (query.matches(".*(进行中|待审核|处理中).*")) return FlowConstants.STATUS_RUNNING;
        if (query.matches(".*(已通过|通过|已完成).*")) return FlowConstants.STATUS_APPROVED;
        if (query.matches(".*(已拒绝|拒绝|驳回).*")) return FlowConstants.STATUS_REJECTED;
        if (query.matches(".*(已撤回|撤回).*")) return FlowConstants.STATUS_CANCELED;
        return null;
    }

    public String flowStatusText(Integer status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case FlowConstants.STATUS_RUNNING: return "进行中";
            case FlowConstants.STATUS_APPROVED: return "已通过";
            case FlowConstants.STATUS_REJECTED: return "已拒绝";
            case FlowConstants.STATUS_CANCELED: return "已撤回";
            default: return String.valueOf(status);
        }
    }

    public String flowTypeText(String flowType) {
        if (flowType == null) {
            return "-";
        }
        switch (flowType) {
            case FlowConstants.FLOW_TYPE_ONBOARD: return "入职";
            case FlowConstants.FLOW_TYPE_REGULAR: return "转正";
            case FlowConstants.FLOW_TYPE_TRANSFER: return "调岗";
            case FlowConstants.FLOW_TYPE_LEAVE: return "离职";
            case FlowConstants.FLOW_TYPE_LEAVE_REQUEST: return "请假";
            case FlowConstants.FLOW_TYPE_CERTIFICATE: return "证明开具";
            case FlowConstants.FLOW_TYPE_MULTI_SIGN: return "会签/或签";
            default: return flowType;
        }
    }

    /** 员工状态文本：0离职 1在职 2试用 */
    public String empStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0: return "离职";
            case STATUS_ACTIVE: return "在职";
            case STATUS_PROBATION: return "试用";
            default: return String.valueOf(status);
        }
    }

    // ==================== 实体查找 ====================

    /** 按姓名模糊查找员工 */
    public Employee findEmployeeByName(String name) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Employee::getEmpName, name);
        List<Employee> employees = employeeService.list(wrapper);
        return employees.isEmpty() ? null : employees.get(0);
    }

    /** 按候选人姓名或简历ID定位简历 */
    public Resume findCandidateResume(String candidate) {
        if (candidate.matches("\\d+")) {
            return resumeService.getById(Long.valueOf(candidate));
        }
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Resume::getResumeName, candidate);
        List<Resume> resumes = resumeService.list(wrapper);
        return resumes.isEmpty() ? null : resumes.get(0);
    }

    /** 解析申请人：优先按查询中的员工姓名，其次取当前登录人 */
    public Employee resolveApplicant(String query) {
        String name = extractSelfServiceEmpName(query);
        if (name != null && !name.isEmpty()) {
            Employee emp = findEmployeeByName(name);
            if (emp != null) {
                return emp;
            }
        }
        Long empId = CurrentUserService.empId();
        return empId == null ? null : employeeService.getById(empId);
    }

    /** 请假单审批状态文本：优先关联流程实例状态 */
    public String leaveRequestStatusText(LeaveRequest request) {
        if (request.getFlowInstanceId() != null) {
            FlowInstance fi = flowInstanceMapper.selectById(request.getFlowInstanceId());
            if (fi != null) {
                return flowStatusText(fi.getFlowStatus());
            }
        }
        if (request.getStatus() != null && request.getStatus() == 1) {
            return "已通过";
        }
        return "审批中";
    }

    /** 按流程实例ID查单号 */
    public String flowNoOf(Long flowInstanceId) {
        if (flowInstanceId == null) {
            return "";
        }
        FlowInstance fi = flowInstanceMapper.selectById(flowInstanceId);
        return fi == null ? "" : fi.getFlowNo();
    }

    // ==================== 实体格式化 ====================

    public String formatEmployeeInfo(Employee employee) {
        return String.format(
                "员工信息:\n" +
                        "- ID: %d\n" +
                        "- 员工编号: %s\n" +
                        "- 姓名: %s\n" +
                        "- 部门: %s\n" +
                        "- 岗位: %s\n" +
                        "- 邮箱: %s\n" +
                        "- 手机号: %s\n" +
                        "- 入职日期: %s\n" +
                        "- 状态: %s",
                employee.getId(), employee.getEmpNo(), employee.getEmpName(),
                employee.getDeptName(), employee.getPositionName(),
                employee.getEmail(), employee.getPhone(),
                employee.getEntryDate(), employee.getEmpStatus());
    }

    public String formatJobInfo(JobPost jobPost) {
        return String.format(
                "岗位信息:\n" +
                        "- ID: %d\n" +
                        "- 岗位编码: %s\n" +
                        "- 岗位名称: %s\n" +
                        "- 所属部门: %s\n" +
                        "- 工作地点: %s\n" +
                        "- 岗位职责: %s\n" +
                        "- 任职要求: %s\n" +
                        "- 薪资下限: %s\n" +
                        "- 薪资上限: %s\n" +
                        "- 学历要求: %s\n" +
                        "- 工作年限要求: %s\n" +
                        "- 招聘人数: %d\n" +
                        "- 岗位状态: %s\n" +
                        "- 是否对外发布: %s",
                jobPost.getId(), jobPost.getJobCode(), jobPost.getJobName(),
                jobPost.getDeptName(), jobPost.getWorkCity(),
                jobPost.getJobDuty(), jobPost.getJobRequirement(),
                jobPost.getSalaryMin(), jobPost.getSalaryMax(),
                jobPost.getEducationReq(), jobPost.getWorkYearReq(),
                jobPost.getHeadCount(), jobPost.getJobStatus(), jobPost.getIsPublic());
    }

    public String formatResumeInfo(Resume resume) {
        return String.format(
                "简历信息:\n" +
                        "- ID: %d\n" +
                        "- 候选人姓名: %s\n" +
                        "- 邮箱: %s\n" +
                        "- 手机号: %s\n" +
                        "- 学历: %s\n" +
                        "- 毕业院校: %s\n" +
                        "- 专业: %s\n" +
                        "- 工作年限: %d\n" +
                        "- 期望岗位: %s\n" +
                        "- 期望薪资下限: %s\n" +
                        "- 期望薪资上限: %s\n" +
                        "- 期望工作城市: %s\n" +
                        "- 目标岗位ID: %d\n" +
                        "- 匹配分数: %s\n" +
                        "- 简历状态: %d",
                resume.getId(), resume.getResumeName(), resume.getEmail(),
                resume.getPhone(), resume.getEducation(), resume.getSchool(),
                resume.getMajor(), resume.getWorkYears(), resume.getExpectPosition(),
                resume.getExpectSalaryMin(), resume.getExpectSalaryMax(),
                resume.getExpectCity(), resume.getTargetJobId(),
                resume.getMatchScore(), resume.getResumeStatus());
    }

    public String formatPerformanceInfo(Performance performance) {
        return String.format(
                "绩效信息:\n" +
                        "- ID: %d\n" +
                        "- 员工ID: %d\n" +
                        "- 绩效周期编码: %s\n" +
                        "- 自评分数: %s\n" +
                        "- 领导评分: %s\n" +
                        "- 最终得分: %s\n" +
                        "- 绩效等级: %s\n" +
                        "- AI评语: %s\n" +
                        "- 状态: %d",
                performance.getId(), performance.getEmpId(), performance.getPeriodCode(),
                performance.getSelfScore(), performance.getLeaderScore(),
                performance.getFinalScore(), performance.getPerformanceLevel(),
                performance.getAiComment(), performance.getStatus());
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(0) : null;
    }
}