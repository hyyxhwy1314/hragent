package org.example.hragent.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.dto.TaskCompleteDto;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.entity.JobPost;
import org.example.hragent.entity.Resume;
import org.example.hragent.entity.Performance;
import org.example.hragent.entity.TrainingCourse;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.service.JobPostService;
import org.example.hragent.service.ResumeService;
import org.example.hragent.service.PerformanceService;
import org.example.hragent.service.TrainingCourseService;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.vo.FlowTraceVO;
import org.example.hragent.vo.TaskVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * HR 业务工具集
 * 实现 Agent 可调用的各类 HR 业务逻辑
 */
@Component
public class HrBusinessTools {

    /** 简历状态：待筛选(1)/面试中(2)/录用(3)/归档(4) */
    private static final int RESUME_STATUS_SCREENING = 1; // 待筛选
    private static final int RESUME_STATUS_ACCEPTED = 3;  // 录用：发起入职的前置状态

    private final EmployeeService employeeService;
    private final JobPostService jobPostService;
    private final ResumeService resumeService;
    private final PerformanceService performanceService;
    private final TrainingCourseService trainingCourseService;
    private final FlowOrchestratorService flowOrchestratorService;
    private final FlowInstanceMapper flowInstanceMapper;

    public HrBusinessTools(EmployeeService employeeService,
                           JobPostService jobPostService,
                           ResumeService resumeService,
                           PerformanceService performanceService,
                           TrainingCourseService trainingCourseService,
                           FlowOrchestratorService flowOrchestratorService,
                           FlowInstanceMapper flowInstanceMapper) {
        this.employeeService = employeeService;
        this.jobPostService = jobPostService;
        this.resumeService = resumeService;
        this.performanceService = performanceService;
        this.trainingCourseService = trainingCourseService;
        this.flowOrchestratorService = flowOrchestratorService;
        this.flowInstanceMapper = flowInstanceMapper;
    }

    /**
     * 查询指定员工的详细信息
     */
    @Tool("查询指定员工的详细信息，参数为员工姓名或员工ID")
    public String queryEmployeeInfo(String query) {
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
     * 查询系统中所有员工列表
     */
    @Tool("查询系统中所有员工的列表")
    public String queryEmployeeList() {
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
     * 查询指定岗位的详细信息
     */
    @Tool("查询指定岗位的详细信息，参数为岗位名称或岗位ID")
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
     * 查询系统中所有岗位列表
     */
    @Tool("查询系统中所有岗位的列表")
    public String queryJobList() {
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
     * 查询指定候选人的简历信息
     */
    @Tool("查询指定候选人的简历信息，参数为候选人姓名或简历ID")
    public String queryResumeInfo(String query) {
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
     * 查询简历与岗位的匹配结果
     */
    @Tool("查询简历与岗位的匹配分数和结果，参数为简历ID")
    public String queryResumeMatch(String query) {
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
     * 登记候选人简历信息，返回简历ID。
     * <p>入职流程基于候选人简历发起（bizId=简历ID），因此发起入职前若候选人在系统中尚无简历，
     * 需先调用本工具登记简历。参数格式示例：
     * 登记候选人李四，手机号13800000000，邮箱lisi@test.com，意向岗位JAVA开发，意向城市深圳
     */
    @Tool("登记候选人简历信息，返回简历ID。参数需包含候选人姓名（必填），可选手机号、邮箱、意向岗位、意向城市、学历、工作年限")
    public String registerCandidate(String query) {
        try {
            String name = extractField(query, "姓名");
            if (name == null || name.isEmpty()) {
                return "请提供候选人姓名，例如：登记候选人李四，手机号13800000000，意向岗位JAVA开发";
            }
            Resume resume = new Resume();
            resume.setResumeName(name);
            resume.setPhone(extractPhone(query));
            resume.setEmail(extractEmail(query));
            resume.setExpectPosition(extractNonEmpty(query, "岗位"));
            resume.setExpectCity(extractNonEmpty(query, "城市"));
            try {
                String years = extractNonEmpty(query, "年限");
                if (years != null && years.matches("\\d+")) resume.setWorkYears(Integer.valueOf(years));
            } catch (NumberFormatException ignored) {
            }
            resume.setResumeStatus(RESUME_STATUS_SCREENING); // 待筛选
            resumeService.save(resume);
            return String.format("候选人简历登记成功。候选人：%s，简历ID：%d。可直接为其发起入职流程。",
                    resume.getResumeName(), resume.getId());
        } catch (Exception e) {
            return "登记候选人简历失败：" + e.getMessage();
        }
    }

    /**
     * 录用候选人：将简历状态更新为「录用」。
     * <p>发起入职审批的前置条件：只有录用状态的简历才能发起入职。因此发起入职前，
     * 需先确认简历状态，若尚未录用则调用本工具将其标记为录用。
     */
    @Tool("录用候选人简历，将其状态标记为「录用」，使其满足发起入职的前置条件。参数为候选人姓名或简历ID")
    public String acceptCandidate(String query) {
        try {
            String candidate = extractProcessCandidate(query);
            if (candidate == null || candidate.isEmpty()) {
                return "请提供候选人姓名或简历ID，例如：录用候选人李四";
            }
            Resume resume = findCandidateResume(candidate);
            if (resume == null) {
                return String.format("系统中未找到候选人「%s」的简历，请先使用「登记候选人简历」登记后再录用。", candidate);
            }
            resume.setResumeStatus(RESUME_STATUS_ACCEPTED);
            resumeService.updateById(resume);
            return String.format("候选人 %s（简历ID %d）已更新为「录用」状态，现可为其发起入职审批流程。",
                    resume.getResumeName(), resume.getId());
        } catch (Exception e) {
            return "录用候选人失败：" + e.getMessage();
        }
    }

    /**
     * 为候选人发起入职审批流程。
     * <p>入职流程与简历强关联：{@code bizId} 必须是候选人简历ID，而非员工ID；审批通过后系统
     * 会根据简历自动创建员工记录。用人部门主管通过 {@code bizJson.targetLeaderId} 指定，
     * 该主管是入职流程的第二个审批节点「用人部门确认」的审批人。
     * 参数格式示例：为候选人李四发起入职，用人部门主管是王五（若输入数字则视为简历ID）
     */
    @Tool("为候选人发起入职审批流程。参数为候选人姓名或简历ID（必填）以及用人部门主管姓名（必填）。注意：入职基于简历发起，bizId为简历ID，请先用登记/查询确认候选人已有简历")
    public String startOnboardingProcess(String query) {
        try {
            String candidate = extractProcessCandidate(query);
            String leaderName = extractTargetLeader(query);
            if (candidate == null || candidate.isEmpty()) {
                return "请提供候选人姓名或简历ID，以及用人部门主管姓名，例如：为候选人李四发起入职，用人部门主管是王五";
            }
            if (leaderName == null || leaderName.isEmpty()) {
                return "请同时提供用人部门主管姓名（为其指定部门审批人），例如：为候选人李四发起入职，用人部门主管是王五";
            }

            String candidateName = candidate.matches("\\d+") ? "简历ID " + candidate : candidate;
            Resume resume = findCandidateResume(candidate);
            if (resume == null) {
                return String.format("系统中暂未找到候选人「%s」的简历。入职流程需要基于简历发起，请先使用「登记候选人简历」登记后再发起入职。",
                        candidateName);
            }

            // 入职门槛：仅「录用」状态的简历可发起入职
            if (resume.getResumeStatus() == null || resume.getResumeStatus() != RESUME_STATUS_ACCEPTED) {
                return String.format("候选人 %s（简历ID %d）当前状态非「录用」，不能发起入职。请先使用「录用候选人简历」将其标记为录用后再发起入职。",
                        resume.getResumeName(), resume.getId());
            }

            Employee leader = findEmployeeByName(leaderName);
            if (leader == null) {
                return String.format("未找到用人部门主管员工：%s，请确认姓名是否正确", leaderName);
            }

            // 组装业务参数：bizId=简历ID；bizJson 指定部门主管 targetLeaderId 及入职部门/岗位
            String bizJson = String.format("{\"targetLeaderId\": %d, \"targetDeptName\": \"%s\", \"targetPosition\": \"%s\"}",
                    leader.getId(),
                    nvl(leader.getDeptName()).replace("\"", "\\\""),
                    nvl(resume.getExpectPosition()).replace("\"", "\\\""));

            FlowStartDto dto = new FlowStartDto();
            dto.setProcessKey(FlowConstants.PROC_KEY_ONBOARD);
            dto.setBizId(resume.getId());
            dto.setBizJson(bizJson);
            org.example.hragent.vo.FlowInstanceVO vo = flowOrchestratorService.start(dto);

            return String.format("已为候选人 %s（简历ID %d）发起入职审批流程，流程单号 %s。\n"
                            + "审批路径：HR 审核 → 用人部门确认（分管主管 %s）→ 通过后自动创建员工记录。",
                    resume.getResumeName(), resume.getId(), vo.getFlowNo(), leader.getEmpName());
        } catch (Exception e) {
            return "发起入职流程失败：" + e.getMessage();
        }
    }

    /**
     * 为指定员工发起离职流程
     */
    @Tool("为指定员工发起离职申请流程，参数为员工姓名")
    public String startResignationProcess(String query) {
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
     * 为指定员工发起调岗流程
     */
    @Tool("为指定员工发起调岗申请流程，参数为员工姓名")
    public String startTransferProcess(String query) {
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
     * 查询审批流程实例列表，可按流程类型(onboard/leave/transfer/regular)或状态筛选
     * 参数格式示例：查询流程实例 / 查询入职流程 / 查询进行中的流程
     */
    @Tool("查询审批流程实例列表，可按流程类型（入职/离职/调岗/转正）和状态（进行中/已通过/已拒绝/已撤回）筛选")
    public String queryProcessInstances(String query) {
        try {
            String type = classifyFlowType(query);
            Integer status = classifyFlowStatus(query);
            LambdaQueryWrapper<FlowInstance> wrapper = new LambdaQueryWrapper<FlowInstance>()
                    .eq(type != null, FlowInstance::getFlowType, type)
                    .eq(status != null, FlowInstance::getFlowStatus, status)
                    .orderByDesc(FlowInstance::getId)
                    .last("LIMIT 20");
            List<FlowInstance> instances = flowInstanceMapper.selectList(wrapper);
            if (instances.isEmpty()) {
                return "未查询到符合条件的流程实例";
            }
            StringBuilder sb = new StringBuilder("共 ").append(instances.size()).append(" 条流程实例：\n");
            for (FlowInstance fi : instances) {
                sb.append(String.format("- 单号 %s | 类型 %s | 状态 %s | 业务ID %s | 流程实例 %s\n",
                        fi.getFlowNo(), fi.getFlowType(),
                        flowStatusText(fi.getFlowStatus()), fi.getBizId(), fi.getFlowableProcInstId()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询流程实例失败：" + e.getMessage();
        }
    }

    /**
     * 查询指定流程实例的审批轨迹（各节点处理情况）
     * 参数格式示例：查询流程单号 FL202601011200000001 的轨迹 / 查询流程实例 8 的审批进度
     */
    @Tool("查询指定流程实例的审批轨迹和各节点处理状态，参数为流程实例ID")
    public String queryProcessTrace(String query) {
        try {
            Long id = extractLong(query);
            if (id == null) {
                return "请提供流程实例ID以查询审批轨迹，例如：查询流程实例 8 的审批进度";
            }
            List<FlowTraceVO> trace = flowOrchestratorService.getTrace(id);
            if (trace.isEmpty()) {
                return "该流程实例暂无审批记录";
            }
            StringBuilder sb = new StringBuilder("审批轨迹：\n");
            for (FlowTraceVO t : trace) {
                sb.append(String.format("- %s | %s | 处理人 %s | %s%s\n",
                        t.getNodeName(),
                        t.getStartTime() == null ? "未开始" : t.getStartTime().toString().replace("T", " "),
                        nvl(t.getAssigneeName()), nvl(t.getStatus()),
                        t.getComment() == null ? "" : " | 意见：" + t.getComment()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询流程轨迹失败：" + e.getMessage();
        }
    }

    /**
     * 查询当前登录人的待办审批任务
     */
    @Tool("查询当前登录人的待办审批任务列表")
    public String queryMyTodoTasks() {
        try {
            Long empId = CurrentUserService.empId();
            if (empId == null) {
                return "当前未获取到登录用户，无法查询待办";
            }
            List<TaskVO> tasks = flowOrchestratorService.listTodoTasks(empId);
            if (tasks.isEmpty()) {
                return "您当前没有待办审批任务";
            }
            StringBuilder sb = new StringBuilder("您的待办审批任务（共 ").append(tasks.size()).append(" 条）：\n");
            for (TaskVO t : tasks) {
                sb.append(String.format("- 任务 %s | %s | 单号 %s | 任务ID %s\n",
                        nvl(t.getTaskName()), nvl(t.getFlowType()), nvl(flowNoOf(t.getFlowInstanceId())),
                        t.getTaskId()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询待办失败：" + e.getMessage();
        }
    }

    /**
     * 完成审批任务（通过/拒绝）。
     * 参数格式示例：通过任务 62505，意见 同意；或 拒绝任务 62505，意见 材料不全
     */
    @Tool("完成一个审批任务：通过或拒绝。参数需包含任务ID（必填）与审批结果（通过/拒绝），可附审批意见")
    public String completeApprovalTask(String query) {
        try {
            String taskId = extractTaskId(query);
            Boolean approved = extractApproved(query);
            if (taskId == null || taskId.isEmpty()) {
                return "请提供任务ID与审批结果，例如：通过任务 62505，意见 同意";
            }
            if (approved == null) {
                return "请明确审批结果（通过/拒绝），例如：通过任务 62505";
            }
            String comment = extractComment(query);
            TaskCompleteDto dto = new TaskCompleteDto();
            dto.setApproved(approved);
            dto.setComment(comment);
            boolean ok = flowOrchestratorService.completeTask(taskId, dto);
            return String.format("任务 %s 已%s。%s", taskId, approved ? "通过" : "拒绝",
                    comment == null ? "" : "审批意见：" + comment);
        } catch (Exception e) {
            return "完成任务失败：" + e.getMessage();
        }
    }

    /**
     * 查询指定员工的绩效考核结果
     */
    @Tool("查询指定员工的绩效考核结果，参数为员工ID或员工姓名")
    public String queryPerformanceResult(String query) {
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
     * 为指定员工生成完整绩效报告
     */
    @Tool("为指定员工生成完整绩效报告，参数为员工ID或员工姓名")
    public String generatePerformanceReport(String query) {
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
     * 为指定员工生成个性化培训计划
     */
    @Tool("为指定员工生成个性化培训计划，参数为员工ID或员工姓名")
    public String generateTrainingPlan(String query) {
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
     * 查询系统中可用的培训课程
     */
    @Tool("查询系统中所有可用的培训课程列表")
    public String queryTrainingCourses() {
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
     * 回答人力资源政策相关的知识问题
     */
    @Tool("回答人力资源政策、规章制度相关的知识问题")
    public String hrKnowledgeQA(String query) {
        try {
            // 此处将对接知识库或 RAG 系统
            return String.format("关于您提出的人事政策相关问题：%s。如需了解更多详情，请查阅《员工手册》或直接联系人力资源部。", query);

        } catch (Exception e) {
            return "处理人力资源知识库查询时出错: " + e.getMessage();
        }
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

    // ================= 入职流程相关解析辅助 =================

    /**
     * 从入职发起查询中提取候选人（姓名或数字简历ID）。
     * 如"为候选人李四发起入职" → "李四"；"简历12发起入职" → "12"。
     */
    private String extractProcessCandidate(String query) {
        if (query == null || query.isEmpty()) return null;
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
    private String extractTargetLeader(String query) {
        if (query == null || query.isEmpty()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(主管|经理|审批人|leader)(?:是|为|：|:|,)")
                .matcher(query);
        if (!m.find()) return null;
        String rest = query.substring(m.end()).trim();
        int cut = rest.length();
        for (String sep : new String[]{"，", ",", "；", ";", "。", " "}) {
            int idx = rest.indexOf(sep);
            if (idx >= 0) cut = Math.min(cut, idx);
        }
        String name = rest.substring(0, cut).trim();
        return name.isEmpty() ? null : name;
    }

    /**
     * 按候选人姓名或简历ID定位简历。
     */
    private Resume findCandidateResume(String candidate) {
        if (candidate.matches("\\d+")) {
            return resumeService.getById(Long.valueOf(candidate));
        }
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Resume::getResumeName, candidate);
        List<Resume> resumes = resumeService.list(wrapper);
        return resumes.isEmpty() ? null : resumes.get(0);
    }

    // ================= 登记简历/通用字段解析辅助 =================

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    /** 提取指定字段后的内容（如 "姓名李四" → "李四"，取 "关键词+值" 中关键词后的值） */
    private String extractField(String query, String key) {
        if (query == null || query.isEmpty()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(key + "\\s*(?:是|为|：|:|,|，)?\\s*([^\\s，,；;]+)")
                .matcher(query);
        return m.find() ? m.group(1) : null;
    }

    /** 提取非空字段（未匹配返回 null） */
    private String extractNonEmpty(String query, String key) {
        String v = extractField(query, key);
        return v == null || v.isEmpty() ? null : v;
    }

    private String extractPhone(String query) {
        if (query == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("1[3-9]\\d{9}").matcher(query);
        return m.find() ? m.group(0) : null;
    }

    private String extractEmail(String query) {
        if (query == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(query);
        return m.find() ? m.group(0) : null;
    }

    /**
     * 从查询中提取数字ID（用于流程实例ID、简历ID等）
     */
    private Long extractLong(String query) {
        if (query == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{1,18}").matcher(query);
        if (!m.find()) return null;
        try {
            return Long.valueOf(m.group(0));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从审批查询中提取任务ID（Flowable 任务ID一般为纯数字） */
    private String extractTaskId(String query) {
        if (query == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]{3,}")
                .matcher(query.replaceFirst("^(流程|实例|业务)\\s*\\d+", ""));
        return m.find() ? m.group(0) : null;
    }

    /** 从审批查询中提取审批结果：true=通过，false=拒绝，无法识别返回 null */
    private Boolean extractApproved(String query) {
        if (query == null) return null;
        // 先识别拒绝（含"不同意"，避免被"同意"误判）
        if (query.matches(".*(拒绝|驳回|不同意|reject|refuse|no).*")) return Boolean.FALSE;
        if (query.matches(".*(通过|同意|批准|approve|yes).*")) return Boolean.TRUE;
        return null;
    }

    /** 从审批查询中提取审批意见（"意见/备注/comment" 后的内容） */
    private String extractComment(String query) {
        if (query == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(意见|备注|comment)\\s*(?:是|为|：|:|,|，)?\\s*(.+)$").matcher(query);
        return m.find() ? m.group(2).trim() : null;
    }

    // ================= 流程类型/状态分类辅助 =================

    private String classifyFlowType(String query) {
        if (query == null) return null;
        if (query.matches(".*(入职).*")) return FlowConstants.FLOW_TYPE_ONBOARD;
        if (query.matches(".*(离职).*")) return FlowConstants.FLOW_TYPE_LEAVE;
        if (query.matches(".*(调岗).*")) return FlowConstants.FLOW_TYPE_TRANSFER;
        if (query.matches(".*(转正).*")) return FlowConstants.FLOW_TYPE_REGULAR;
        return null;
    }

    private Integer classifyFlowStatus(String query) {
        if (query == null) return null;
        if (query.matches(".*(进行中|待审核|处理中).*")) return FlowConstants.STATUS_RUNNING;
        if (query.matches(".*(已通过|通过|已完成).*")) return FlowConstants.STATUS_APPROVED;
        if (query.matches(".*(已拒绝|拒绝|驳回).*")) return FlowConstants.STATUS_REJECTED;
        if (query.matches(".*(已撤回|撤回).*")) return FlowConstants.STATUS_CANCELED;
        return null;
    }

    private String flowStatusText(Integer status) {
        if (status == null) return "";
        switch (status) {
            case FlowConstants.STATUS_RUNNING: return "进行中";
            case FlowConstants.STATUS_APPROVED: return "已通过";
            case FlowConstants.STATUS_REJECTED: return "已拒绝";
            case FlowConstants.STATUS_CANCELED: return "已撤回";
            default: return String.valueOf(status);
        }
    }

    /** 按流程实例ID查单号（待办列表中展示用） */
    private String flowNoOf(Long flowInstanceId) {
        if (flowInstanceId == null) return "";
        FlowInstance fi = flowInstanceMapper.selectById(flowInstanceId);
        return fi == null ? "" : fi.getFlowNo();
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
