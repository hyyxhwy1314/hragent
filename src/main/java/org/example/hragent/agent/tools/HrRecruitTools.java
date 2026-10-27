package org.example.hragent.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.Resume;
import org.example.hragent.flow.support.BizFlowStarter;
import org.example.hragent.service.ResumeService;
import org.example.hragent.vo.FlowInstanceVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 招聘与简历工具：简历查询/登记/录用，以及基于简历发起入职流程。
 */
@Component
public class HrRecruitTools implements HrToolSet {

    /** 简历状态：待筛选 */
    private static final int RESUME_STATUS_SCREENING = 1;

    private final ResumeService resumeService;
    private final BizFlowStarter bizFlowStarter;
    private final HrToolSupport support;

    public HrRecruitTools(ResumeService resumeService,
                          BizFlowStarter bizFlowStarter,
                          HrToolSupport support) {
        this.resumeService = resumeService;
        this.bizFlowStarter = bizFlowStarter;
        this.support = support;
    }

    /** 查询指定候选人的简历信息 */
    @Tool("查询指定候选人的简历信息，参数为候选人姓名或简历ID")
    public String queryResumeInfo(String query) {
        try {
            String searchTerm = support.extractSearchTerm(query);
            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供简历ID或候选人姓名";
            }

            Resume resume = null;
            try {
                resume = resumeService.getById(Long.parseLong(searchTerm));
            } catch (NumberFormatException e) {
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
            return support.formatResumeInfo(resume);
        } catch (Exception e) {
            return "查询简历信息出错：" + e.getMessage();
        }
    }

    /** 查询简历与岗位的匹配结果 */
    @Tool("查询简历与岗位的匹配分数和结果，参数为简历ID")
    public String queryResumeMatch(String query) {
        try {
            String searchTerm = support.extractSearchTerm(query);
            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供简历ID进行匹配查询";
            }
            try {
                Resume resume = resumeService.getById(Long.parseLong(searchTerm));
                if (resume == null) {
                    return String.format("未找到ID为 %s 的简历", searchTerm);
                }
                return String.format("简历 ID %d - 候选人：%s, 匹配分数：%s, 目标岗位ID：%s",
                        resume.getId(), resume.getResumeName(),
                        resume.getMatchScore() != null ? resume.getMatchScore() : "未计算",
                        resume.getTargetJobId());
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
            String name = support.extractField(query, "姓名");
            if (name == null || name.isEmpty()) {
                return "请提供候选人姓名，例如：登记候选人李四，手机号13800000000，意向岗位JAVA开发";
            }
            Resume resume = new Resume();
            resume.setResumeName(name);
            resume.setPhone(support.extractPhone(query));
            resume.setEmail(support.extractEmail(query));
            resume.setExpectPosition(support.extractNonEmpty(query, "岗位"));
            resume.setExpectCity(support.extractNonEmpty(query, "城市"));
            try {
                String years = support.extractNonEmpty(query, "年限");
                if (years != null && years.matches("\\d+")) {
                    resume.setWorkYears(Integer.valueOf(years));
                }
            } catch (NumberFormatException ignored) {
            }
            resume.setResumeStatus(RESUME_STATUS_SCREENING);
            resumeService.save(resume);
            return String.format("候选人简历登记成功。候选人：%s，简历ID：%d。可直接为其发起入职流程。",
                    resume.getResumeName(), resume.getId());
        } catch (Exception e) {
            return "登记候选人简历失败：" + e.getMessage();
        }
    }

    /**
     * 录用候选人：将简历状态更新为「录用」，使其满足发起入职的前置条件。
     */
    @Tool("录用候选人简历，将其状态标记为「录用」，使其满足发起入职的前置条件。参数为候选人姓名或简历ID")
    public String acceptCandidate(String query) {
        try {
            String candidate = support.extractProcessCandidate(query);
            if (candidate == null || candidate.isEmpty()) {
                return "请提供候选人姓名或简历ID，例如：录用候选人李四";
            }
            Resume resume = support.findCandidateResume(candidate);
            if (resume == null) {
                return String.format("系统中未找到候选人「%s」的简历，请先使用「登记候选人简历」登记后再录用。", candidate);
            }
            resume.setResumeStatus(HrToolSupport.RESUME_STATUS_ACCEPTED);
            resumeService.updateById(resume);
            return String.format("候选人 %s（简历ID %d）已更新为「录用」状态，现可为其发起入职审批流程。",
                    resume.getResumeName(), resume.getId());
        } catch (Exception e) {
            return "录用候选人失败：" + e.getMessage();
        }
    }

    /**
     * 为候选人发起入职审批流程。
     * <p>入职流程与简历强关联：{@code bizId} 必须是候选人简历ID；审批通过后系统会根据简历自动创建员工记录。
     * 用人部门主管通过 {@code bizJson.targetLeaderId} 指定，是入职流程「用人部门确认」节点的审批人。
     * 参数格式示例：为候选人李四发起入职，用人部门主管是王五（若输入数字则视为简历ID）
     */
    @Tool("为候选人发起入职审批流程。参数为候选人姓名或简历ID（必填）以及用人部门主管姓名（必填）。注意：入职基于简历发起，bizId为简历ID，请先用登记/查询确认候选人已有简历")
    public String startOnboardingProcess(String query) {
        try {
            String candidate = support.extractProcessCandidate(query);
            String leaderName = support.extractTargetLeader(query);
            if (candidate == null || candidate.isEmpty()) {
                return "请提供候选人姓名或简历ID，以及用人部门主管姓名，例如：为候选人李四发起入职，用人部门主管是王五";
            }
            if (leaderName == null || leaderName.isEmpty()) {
                return "请同时提供用人部门主管姓名（为其指定部门审批人），例如：为候选人李四发起入职，用人部门主管是王五";
            }

            String candidateName = candidate.matches("\\d+") ? "简历ID " + candidate : candidate;
            Resume resume = support.findCandidateResume(candidate);
            if (resume == null) {
                return String.format("系统中暂未找到候选人「%s」的简历。入职流程需要基于简历发起，请先使用「登记候选人简历」登记后再发起入职。",
                        candidateName);
            }

            // 入职门槛：仅「录用」状态的简历可发起入职
            if (resume.getResumeStatus() == null || resume.getResumeStatus() != HrToolSupport.RESUME_STATUS_ACCEPTED) {
                return String.format("候选人 %s（简历ID %d）当前状态非「录用」，不能发起入职。请先使用「录用候选人简历」将其标记为录用后再发起入职。",
                        resume.getResumeName(), resume.getId());
            }

            Employee leader = support.findEmployeeByName(leaderName);
            if (leader == null) {
                return String.format("未找到用人部门主管员工：%s，请确认姓名是否正确", leaderName);
            }

            Map<String, Object> bizVars = new LinkedHashMap<>();
            bizVars.put("targetLeaderId", leader.getId());
            bizVars.put("targetDeptName", support.nvl(leader.getDeptName()));
            bizVars.put("targetPosition", support.nvl(resume.getExpectPosition()));
            FlowInstanceVO vo = bizFlowStarter.start(
                    FlowConstants.PROC_KEY_ONBOARD, resume.getId(), bizVars, "入职");

            return String.format("已为候选人 %s（简历ID %d）发起入职审批流程，流程单号 %s。\n"
                            + "审批路径：HR 审核 → 用人部门确认（分管主管 %s）→ 通过后自动创建员工记录。",
                    resume.getResumeName(), resume.getId(), vo.getFlowNo(), leader.getEmpName());
        } catch (Exception e) {
            return "发起入职流程失败：" + e.getMessage();
        }
    }
}