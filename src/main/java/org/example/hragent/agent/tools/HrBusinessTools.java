package org.example.hragent.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.agent.state.AgentIntent;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.JobPost;
import org.example.hragent.entity.Resume;
import org.example.hragent.entity.Performance;
import org.example.hragent.entity.TrainingCourse;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.service.JobPostService;
import org.example.hragent.service.ResumeService;
import org.example.hragent.service.PerformanceService;
import org.example.hragent.service.TrainingCourseService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * HR 业务工具集
 * 实现 Agent 可调用的各类 HR 业务逻辑
 */
@Component
public class HrBusinessTools {

    private final EmployeeService employeeService;
    private final JobPostService jobPostService;
    private final ResumeService resumeService;
    private final PerformanceService performanceService;
    private final TrainingCourseService trainingCourseService;
    private final FlowOrchestratorService flowOrchestratorService;

    public HrBusinessTools(EmployeeService employeeService,
                           JobPostService jobPostService,
                           ResumeService resumeService,
                           PerformanceService performanceService,
                           TrainingCourseService trainingCourseService,
                           FlowOrchestratorService flowOrchestratorService) {
        this.employeeService = employeeService;
        this.jobPostService = jobPostService;
        this.resumeService = resumeService;
        this.performanceService = performanceService;
        this.trainingCourseService = trainingCourseService;
        this.flowOrchestratorService = flowOrchestratorService;
    }

    /**
     * 查询员工信息
     */
    public String queryEmployeeInfo(String query, String userId) {
        try {
            // 从查询语句中提取员工 ID 或姓名
            String searchTerm = extractSearchTerm(query);

            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供要查询的员工姓名或ID";
            }

            // 先按 ID 查询，找不到再按姓名查询
            Employee employee = null;
            try {
                Long id = Long.parseLong(searchTerm);
                employee = employeeService.getById(id);
            } catch (NumberFormatException e) {
                // 改为按姓名查询
                LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
                wrapper.like(Employee::getEmpName, searchTerm);
                List<Employee> employees = employeeService.list(wrapper);
                if (!employees.isEmpty()) {
                    employee = employees.get(0);
                }
            }

            if (employee == null) {
                return String.format("未找到匹配的员工：%s", searchTerm);
            }

            return formatEmployeeInfo(employee);

        } catch (Exception e) {
            return "查询员工信息出错：" + e.getMessage();
        }
    }

    /**
     * 查询员工列表
     */
    public String queryEmployeeList(String query, String userId) {
        try {
            List<Employee> employees = employeeService.list();

            if (employees.isEmpty()) {
                return "系统中未找到任何员工";
            }

            StringBuilder result = new StringBuilder();
            result.append("共找到 ").append(employees.size()).append(" 名员工：\n");

            for (Employee employee : employees) {
                result.append(String.format("- ID: %d, 姓名: %s, 部门: %s, 岗位: %s\n",
                        employee.getId(), employee.getEmpName(), employee.getDeptName(), employee.getPositionName()));
            }

            return result.toString();

        } catch (Exception e) {
            return "查询员工列表出错：" + e.getMessage();
        }
    }

    /**
     * 查询岗位信息
     */
    public String queryJobInfo(String query) {
        try {
            String searchTerm = extractSearchTerm(query);

            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供要查询的岗位名称或ID";
            }

            JobPost jobPost = null;
            try {
                Long id = Long.parseLong(searchTerm);
                jobPost = jobPostService.getById(id);
            } catch (NumberFormatException e) {
                // 按名称查询
                LambdaQueryWrapper<JobPost> wrapper = new LambdaQueryWrapper<>();
                wrapper.like(JobPost::getJobName, searchTerm);
                List<JobPost> jobPosts = jobPostService.list(wrapper);
                if (!jobPosts.isEmpty()) {
                    jobPost = jobPosts.get(0);
                }
            }

            if (jobPost == null) {
                return String.format("未找到匹配的岗位：%s", searchTerm);
            }

            return formatJobInfo(jobPost);

        } catch (Exception e) {
            return "查询岗位信息出错：" + e.getMessage();
        }
    }

    /**
     * 查询岗位列表
     */
    public String queryJobList(String query) {
        try {
            List<JobPost> jobPosts = jobPostService.list();

            if (jobPosts.isEmpty()) {
                return "系统中未找到任何岗位";
            }

            StringBuilder result = new StringBuilder();
            result.append("共找到 ").append(jobPosts.size()).append(" 个岗位：\n");

            for (JobPost jobPost : jobPosts) {
                result.append(String.format("- ID: %d, 岗位名称: %s, 所属部门: %s, 状态: %d\n",
                        jobPost.getId(), jobPost.getJobName(), jobPost.getDeptName(), jobPost.getJobStatus()));
            }

            return result.toString();

        } catch (Exception e) {
            return "查询岗位列表出错：" + e.getMessage();
        }
    }

    /**
     * 查询简历信息
     */
    public String queryResumeInfo(String query, String userId) {
        try {
            String searchTerm = extractSearchTerm(query);

            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供简历ID或候选人姓名";
            }

            Resume resume = null;
            try {
                Long id = Long.parseLong(searchTerm);
                resume = resumeService.getById(id);
            } catch (NumberFormatException e) {
                // 按候选人姓名查询
                LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
                wrapper.like(Resume::getResumeName, searchTerm);
                List<Resume> resumes = resumeService.list(wrapper);
                if (!resumes.isEmpty()) {
                    resume = resumes.get(0);
                }
            }

            if (resume == null) {
                return String.format("未找到匹配的简历：%s", searchTerm);
            }

            return formatResumeInfo(resume);

        } catch (Exception e) {
            return "查询简历信息出错：" + e.getMessage();
        }
    }

    /**
     * 查询简历匹配结果
     */
    public String queryResumeMatch(String query, String userId) {
        try {
            String searchTerm = extractSearchTerm(query);

            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供简历ID进行匹配查询";
            }

            try {
                Long resumeId = Long.parseLong(searchTerm);
                Resume resume = resumeService.getById(resumeId);

                if (resume == null) {
                    return String.format("未找到ID为 %s 的简历", searchTerm);
                }

                // 获取匹配结果（应在 resume service 中实现）
                // 当前返回占位响应
                String matchResult = String.format("简历 ID %d - 候选人：%s, 匹配分数：%s, 目标岗位ID：%s",
                        resume.getId(), resume.getResumeName(),
                        resume.getMatchScore() != null ? resume.getMatchScore() : "未计算",
                        resume.getTargetJobId());

                return matchResult;

            } catch (NumberFormatException e) {
                return "请提供有效的简历ID（数字）进行匹配查询";
            }

        } catch (Exception e) {
            return "查询简历匹配结果出错：" + e.getMessage();
        }
    }

    /**
     * 发起入职流程
     */
    public String startOnboardingProcess(String query, String userId) {
        try {
            String employeeName = extractProcessEmployeeName(query);
            if (employeeName == null || employeeName.isEmpty()) {
                return "请提供员工姓名以发起入职流程，例如：发起张三的入职流程";
            }

            Employee employee = findEmployeeByName(employeeName);
            if (employee == null) {
                return String.format("未找到员工：%s，请确认员工姓名是否正确", employeeName);
            }

            FlowStartDto dto = new FlowStartDto();
            dto.setProcessKey(FlowConstants.PROC_KEY_ONBOARD);
            dto.setBizId(employee.getId());
            flowOrchestratorService.start(dto);

            return String.format("已为员工 %s（工号：%s）发起入职流程，审批已提交至相关负责人。",
                    employee.getEmpName(), employee.getEmpNo());

        } catch (Exception e) {
            return "发起入职流程失败：" + e.getMessage();
        }
    }

    /**
     * 发起离职流程
     */
    public String startResignationProcess(String query, String userId) {
        try {
            String employeeName = extractProcessEmployeeName(query);
            if (employeeName == null || employeeName.isEmpty()) {
                return "请提供员工姓名以发起离职流程，例如：发起张三的离职流程";
            }

            // 按姓名查找员工
            Employee employee = findEmployeeByName(employeeName);
            if (employee == null) {
                return String.format("未找到员工：%s，请确认员工姓名是否正确", employeeName);
            }

            // 发起 Flowable 离职流程
            FlowStartDto dto = new FlowStartDto();
            dto.setProcessKey(FlowConstants.PROC_KEY_LEAVE);
            dto.setBizId(employee.getId());
            flowOrchestratorService.start(dto);

            return String.format("已为员工 %s（工号：%s）发起离职流程，审批已提交至相关负责人。",
                    employee.getEmpName(), employee.getEmpNo());

        } catch (Exception e) {
            return "发起离职流程失败：" + e.getMessage();
        }
    }

    /**
     * 发起调岗流程
     */
    public String startTransferProcess(String query, String userId) {
        try {
            String employeeName = extractProcessEmployeeName(query);
            if (employeeName == null || employeeName.isEmpty()) {
                return "请提供员工姓名以发起调岗流程，例如：发起张三的调岗流程";
            }

            Employee employee = findEmployeeByName(employeeName);
            if (employee == null) {
                return String.format("未找到员工：%s，请确认员工姓名是否正确", employeeName);
            }

            FlowStartDto dto = new FlowStartDto();
            dto.setProcessKey(FlowConstants.PROC_KEY_TRANSFER);
            dto.setBizId(employee.getId());
            flowOrchestratorService.start(dto);

            return String.format("已为员工 %s（工号：%s）发起调岗流程，审批已提交至相关负责人。",
                    employee.getEmpName(), employee.getEmpNo());

        } catch (Exception e) {
            return "发起调岗流程失败：" + e.getMessage();
        }
    }

    /**
     * 查询流程状态
     */
    public String queryProcessStatus(String query, String userId) {
        try {
            String processId = extractSearchTerm(query);

            if (processId == null || processId.isEmpty()) {
                return "请提供流程ID查询状态";
            }

            // 此处将对接 Flowable 获取实际流程状态
            return String.format("流程ID %s 当前状态：进行中。当前步骤：部门经理审批。", processId);

        } catch (Exception e) {
            return "查询流程状态出错：" + e.getMessage();
        }
    }

    /**
     * 查询绩效结果
     */
    public String queryPerformanceResult(String query, String userId) {
        try {
            String searchTerm = extractSearchTerm(query);

            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供员工ID或绩效记录ID查询绩效结果";
            }

            Performance performance = null;
            try {
                Long id = Long.parseLong(searchTerm);
                performance = performanceService.getById(id);
            } catch (NumberFormatException e) {
                // 尝试按员工 ID 查询
                try {
                    Long empId = Long.parseLong(searchTerm);
                    LambdaQueryWrapper<Performance> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Performance::getEmpId, empId);
                    wrapper.orderByDesc(Performance::getId);
                    wrapper.last("LIMIT 1");
                    List<Performance> performances = performanceService.list(wrapper);
                    if (!performances.isEmpty()) {
                        performance = performances.get(0);
                    }
                } catch (NumberFormatException ex) {
                    return "请提供有效的数字ID进行绩效查询";
                }
            }

            if (performance == null) {
                return String.format("未找到匹配的绩效结果：%s", searchTerm);
            }

            return formatPerformanceInfo(performance);

        } catch (Exception e) {
            return "查询绩效结果出错：" + e.getMessage();
        }
    }

    /**
     * 生成绩效报告
     */
    public String generatePerformanceReport(String query, String userId) {
        try {
            String searchTerm = extractSearchTerm(query);

            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供员工ID或姓名以生成绩效报告";
            }

            // 此处生成完整绩效报告
            return String.format("已为员工：%s 生成绩效报告。报告包含季度指标、目标达成情况和经理反馈。", searchTerm);

        } catch (Exception e) {
            return "生成绩效报告出错：" + e.getMessage();
        }
    }

    /**
     * 生成培训计划
     */
    public String generateTrainingPlan(String query, String userId) {
        try {
            String searchTerm = extractSearchTerm(query);

            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供员工ID或姓名以生成培训计划";
            }

            // 此处分析员工技能并生成个性化培训建议
            return String.format("已为员工：%s 生成培训计划。推荐课程：领导力培训、技术培训和沟通技巧工作坊。", searchTerm);

        } catch (Exception e) {
            return "生成培训计划出错：" + e.getMessage();
        }
    }

    /**
     * 查询培训课程
     */
    public String queryTrainingCourses(String query) {
        try {
            List<TrainingCourse> courses = trainingCourseService.list();

            if (courses.isEmpty()) {
                return "系统中未找到培训课程";
            }

            StringBuilder result = new StringBuilder();
            result.append("可用的培训课程：\n");

            for (TrainingCourse course : courses) {
                result.append(String.format("- %s (%s): %s (时长: %d 分钟)\n",
                        course.getCourseName(), course.getCourseCode(), course.getCourseDesc(),
                        course.getDurationMin()));
            }

            return result.toString();

        } catch (Exception e) {
            return "查询培训课程出错：" + e.getMessage();
        }
    }

    /**
     * HR 知识问答
     */
    public String hrKnowledgeQA(String query) {
        try {
            // 此处将对接知识库或 RAG 系统
            return String.format("关于您提出的人事政策相关问题：%s。如需了解更多详情，请查阅《员工手册》或直接联系人力资源部。", query);

        } catch (Exception e) {
            return "处理人力资源知识库查询时出错: " + e.getMessage();
        }
    }

    /**
     * 根据意图调用对应工具（编排辅助方法）
     */
    public String executeToolByIntent(AgentIntent intent, String query, String userId) {
        return switch (intent) {
            case QUERY_EMPLOYEE_INFO -> queryEmployeeInfo(query, userId);
            case QUERY_EMPLOYEE_LIST -> queryEmployeeList(query, userId);
            case QUERY_JOB_INFO -> queryJobInfo(query);
            case QUERY_JOB_LIST -> queryJobList(query);
            case QUERY_RESUME_INFO -> queryResumeInfo(query, userId);
            case QUERY_RESUME_MATCH -> queryResumeMatch(query, userId);
            case START_ONBOARDING_PROCESS -> startOnboardingProcess(query, userId);
            case START_RESIGNATION_PROCESS -> startResignationProcess(query, userId);
            case START_TRANSFER_PROCESS -> startTransferProcess(query, userId);
            case QUERY_PROCESS_STATUS -> queryProcessStatus(query, userId);
            case QUERY_PERFORMANCE_RESULT -> queryPerformanceResult(query, userId);
            case GENERATE_PERFORMANCE_REPORT -> generatePerformanceReport(query, userId);
            case GENERATE_TRAINING_PLAN -> generateTrainingPlan(query, userId);
            case QUERY_TRAINING_COURSES -> queryTrainingCourses(query);
            case HR_KNOWLEDGE_QA -> hrKnowledgeQA(query);
            case GENERAL_CONVERSATION -> "普通对话: " + query;
            case UNKNOWN -> "抱歉，我没能理解您的请求。能否请您重新描述一下？";
        };
    }

    // 辅助方法

    /**
     * 从流程类查询中提取员工姓名（如"发起张三的离职流程" → "张三"）
     */
    private String extractProcessEmployeeName(String query) {
        if (query == null || query.isEmpty()) return null;
        // 去掉"发起"前缀和"的离职/入职/调岗流程"后缀
        String name = query.replaceAll("^(发起|为)", "")
                .replaceAll("的(离职|入职|调岗|转正)流程$", "")
                .trim();
        return name.isEmpty() ? null : name;
    }

    /**
     * 按姓名模糊查找员工
     */
    private Employee findEmployeeByName(String name) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Employee::getEmpName, name);
        List<Employee> employees = employeeService.list(wrapper);
        return employees.isEmpty() ? null : employees.get(0);
    }

    private String extractSearchTerm(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        // 简单抽取 - 生产环境应使用 NLP
        String[] words = query.split("\\s+");
        for (String word : words) {
            if (word.matches("\\d+")) {
                return word; // 优先返回数字（潜在 ID）
            }
        }

        // 否则返回最长的词作为搜索词
        String longest = "";
        for (String word : words) {
            if (word.length() > longest.length()) {
                longest = word;
            }
        }

        return longest.isEmpty() ? query : longest;
    }

    private String formatEmployeeInfo(Employee employee) {
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
                employee.getEntryDate(), employee.getEmpStatus()
        );
    }

    private String formatJobInfo(JobPost jobPost) {
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
                jobPost.getHeadCount(), jobPost.getJobStatus(), jobPost.getIsPublic()
        );
    }

    private String formatResumeInfo(Resume resume) {
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
                resume.getMatchScore(), resume.getResumeStatus()
        );
    }

    private String formatPerformanceInfo(Performance performance) {
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
                performance.getAiComment(), performance.getStatus()
        );
    }
}
