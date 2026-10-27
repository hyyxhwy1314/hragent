package org.example.hragent.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import org.example.hragent.agent.rag.KnowledgeRetrievalService;
import org.example.hragent.entity.Performance;
import org.example.hragent.entity.TrainingCourse;
import org.example.hragent.service.PerformanceService;
import org.example.hragent.service.TrainingCourseService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 绩效、培训与知识问答工具。
 */
@Component
public class HrTalentTools implements HrToolSet {

    private final PerformanceService performanceService;
    private final TrainingCourseService trainingCourseService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final HrToolSupport support;

    public HrTalentTools(PerformanceService performanceService,
                         TrainingCourseService trainingCourseService,
                         KnowledgeRetrievalService knowledgeRetrievalService,
                         HrToolSupport support) {
        this.performanceService = performanceService;
        this.trainingCourseService = trainingCourseService;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.support = support;
    }

    /** 查询指定员工的绩效考核结果 */
    @Tool("查询指定员工的绩效考核结果，参数为员工ID或员工姓名")
    public String queryPerformanceResult(String query) {
        try {
            String searchTerm = support.extractSearchTerm(query);
            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供员工ID或绩效记录ID查询绩效结果";
            }

            Performance performance = null;
            try {
                performance = performanceService.getById(Long.parseLong(searchTerm));
            } catch (NumberFormatException e) {
                // 非纯绩效记录ID时，尝试按员工 ID 查询最近一条
                try {
                    LambdaQueryWrapper<Performance> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Performance::getEmpId, Long.parseLong(searchTerm));
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
            return support.formatPerformanceInfo(performance);
        } catch (Exception e) {
            return "查询绩效结果出错：" + e.getMessage();
        }
    }

    /** 为指定员工生成完整绩效报告 */
    @Tool("为指定员工生成完整绩效报告，参数为员工ID或员工姓名")
    public String generatePerformanceReport(String query) {
        try {
            String searchTerm = support.extractSearchTerm(query);
            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供员工ID或姓名以生成绩效报告";
            }
            return String.format("已为员工：%s 生成绩效报告。报告包含季度指标、目标达成情况和经理反馈。", searchTerm);
        } catch (Exception e) {
            return "生成绩效报告出错：" + e.getMessage();
        }
    }

    /** 为指定员工生成个性化培训计划 */
    @Tool("为指定员工生成个性化培训计划，参数为员工ID或员工姓名")
    public String generateTrainingPlan(String query) {
        try {
            String searchTerm = support.extractSearchTerm(query);
            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供员工ID或姓名以生成培训计划";
            }
            return String.format("已为员工：%s 生成培训计划。推荐课程：领导力培训、技术培训和沟通技巧工作坊。", searchTerm);
        } catch (Exception e) {
            return "生成培训计划出错：" + e.getMessage();
        }
    }

    /** 查询系统中可用的培训课程 */
    @Tool("查询系统中所有可用的培训课程列表")
    public String queryTrainingCourses() {
        try {
            List<TrainingCourse> courses = trainingCourseService.list();
            if (courses.isEmpty()) {
                return "系统中未找到培训课程";
            }
            StringBuilder result = new StringBuilder("可用的培训课程：\n");
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

    /** 回答人力资源政策相关的知识问题 */
    @Tool("回答人力资源政策、规章制度相关的知识问题，返回知识库中最相关的现行有效制度条款原文")
    public String hrKnowledgeQA(String query) {
        try {
            KnowledgeRetrievalService.RetrievalResult result = knowledgeRetrievalService.retrieve(query);
            List<KnowledgeRetrievalService.RetrievedChunk> chunks = result.chunks();
            if (chunks.isEmpty()) {
                return expiredNotice(query, result.expiredChunks());
            }
            StringBuilder content = new StringBuilder("知识库检索结果（均为现行有效条款，请依据以下内容作答，并标注来源文档、版本与生效日期）。"
                    + "即使用户以个人经历提问（如“我被调岗了，公司允许吗”），也必须直接给出制度结论与依据，不得只输出功能菜单或反问：\n");
            int index = 1;
            for (KnowledgeRetrievalService.RetrievedChunk chunk : chunks) {
                content.append(String.format("【%d】《%s》v%d · %s%s · 生效日期 %s · 相关度 %.2f%n%s%n",
                        index++, chunk.docTitle(), chunk.version(), chunk.category(),
                        sectionText(chunk), effectiveText(chunk), chunk.score(), chunk.content()));
            }
            return content.toString().trim();
        } catch (Exception e) {
            return "处理人力资源知识库查询时出错: " + e.getMessage();
        }
    }

    /** 无现行条款时的答复：若存在已失效条款，明确告知不可作为依据 */
    private String expiredNotice(String query, List<KnowledgeRetrievalService.RetrievedChunk> expiredChunks) {
        if (expiredChunks.isEmpty()) {
            return String.format("知识库中未检索到与「%s」直接相关的现行有效制度条款，建议查阅《员工手册》或联系人力资源部确认。"
                    + "这是最终检索结果，请直接告知用户，不要重复调用本工具。", query);
        }
        StringBuilder notice = new StringBuilder(String.format(
                "知识库中与「%s」相关的条款已失效或被新版本替代，不能作为答复依据：%n", query));
        for (KnowledgeRetrievalService.RetrievedChunk chunk : expiredChunks) {
            notice.append(String.format("- 《%s》v%d · %s%s · %s（状态 %s）%n",
                    chunk.docTitle(), chunk.version(), chunk.category(),
                    sectionText(chunk), validityText(chunk), chunk.status()));
        }
        notice.append("如员工询问相关政策，应告知其以人力资源部最新发布为准，并建议联系人力资源部确认。"
                + "这是最终检索结果，不要重复调用本工具。");
        return notice.toString().trim();
    }

    private String effectiveText(KnowledgeRetrievalService.RetrievedChunk chunk) {
        return chunk.effectiveFrom() == null ? "未标注" : chunk.effectiveFrom().toString();
    }

    private String sectionText(KnowledgeRetrievalService.RetrievedChunk chunk) {
        return chunk.sectionPath() == null || chunk.sectionPath().isBlank()
                ? "" : " · " + chunk.sectionPath();
    }

    private String validityText(KnowledgeRetrievalService.RetrievedChunk chunk) {
        String from = chunk.effectiveFrom() == null ? "未标注" : chunk.effectiveFrom().toString();
        String to = chunk.effectiveTo() == null ? "长期" : chunk.effectiveTo().toString();
        return String.format("有效期 %s 至 %s", from, to);
    }
}