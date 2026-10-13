package org.example.hragent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.AbilityTag;
import org.example.hragent.entity.FileEntity;
import org.example.hragent.entity.Resume;
import org.example.hragent.service.AbilityTagService;
import org.example.hragent.service.FileService;
import org.example.hragent.service.ResumeParserService;
import org.example.hragent.service.ResumeService;
import org.example.hragent.service.ResumeDeepAnalysisService;
import org.example.hragent.vo.ResumeDeepAnalysisVO;
import org.example.hragent.vo.ResumeParsedData;
import org.example.hragent.vo.ResumeTagMatchVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 简历 AI 深度分析服务实现
 * <p>
 * 链路：根据简历ID下载附件 → 本地解析提取简历全文（PDFBox / OCR）→
 * 调用 {@link ChatLanguageModel}（DeepSeek）生成结构化的深度评估（优势/短板/匹配分析/建议）。
 * 模型输出约定为严格的 JSON，解析失败时降级为纯文本评价。
 */
@Slf4j
@Service
public class ResumeDeepAnalysisServiceImpl implements ResumeDeepAnalysisService {

    private static final String SYSTEM_PROMPT = """
            你是一名资深人力资源招聘专家，负责对候选人简历做深度评估。
            你的输出必须严格是一个合法的 JSON 对象，不要输出任何多余的文字、解释或 Markdown 代码块。
            输出 JSON 结构如下：
            {
              "summary": "对候选人的整体评价（2-4 句）",
              "strengths": ["核心优势1", "核心优势2"],
              "weaknesses": ["潜在短板或风险1"],
              "matchAnalysis": "候选人整体匹配度与适配岗位的分析",
              "suggestions": ["招聘/面试建议1"],
              "score": 78,
              "tagMatch": [
                {"category": "技术技能", "score": 85, "matched": ["Java", "MySQL"], "missing": ["Redis"]},
                {"category": "软技能", "score": 70, "matched": ["沟通协作"], "missing": []}
              ]
            }

            要求：
            1. strengths 给出 3-5 条最突出的优势；weaknesses 给出 2-4 条，若无明显短板可为空数组。
            2. 结合简历中的教育背景、工作经历、技能与项目经验进行客观分析，避免空泛套话。
            3. score 为综合评分（0-100 整数），需依据下方提供的【系统技能标签库】逐条比对简历技能与经历，评估候选人对各类标签的覆盖程度后给出；comprehensive score 应体现整体匹配度。
            4. tagMatch 为「系统技能标签库」中每个分类的匹配明细：score 为该分类得分（0-100 整数），matched 为简历中明显体现并命中的标签名称，missing 为该分类下简历未体现、但对岗位较重要的标签名称；matched/missing 只能从系统技能标签库中挑选标签名称，不得自行编造。
            5. 全部使用中文。
            """;

    private final ResumeService resumeService;
    private final FileService fileService;
    private final ResumeParserService resumeParserService;
    private final ChatLanguageModel chatLanguageModel;
    private final AbilityTagService abilityTagService;
    private final ObjectMapper objectMapper;

    public ResumeDeepAnalysisServiceImpl(ResumeService resumeService,
                                         FileService fileService,
                                         ResumeParserService resumeParserService,
                                         ChatLanguageModel chatLanguageModel,
                                         AbilityTagService abilityTagService,
                                         ObjectMapper objectMapper) {
        this.resumeService = resumeService;
        this.fileService = fileService;
        this.resumeParserService = resumeParserService;
        this.chatLanguageModel = chatLanguageModel;
        this.abilityTagService = abilityTagService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResumeDeepAnalysisVO analyzeDeep(Long resumeId) {
        ResumeDeepAnalysisVO vo = new ResumeDeepAnalysisVO();
        try {
            Resume resume = resumeService.getByIdChecked(resumeId);
            if (resume.getResumeFileId() == null) {
                return fail("该简历未上传附件，无法进行深度分析");
            }
            FileEntity fileEntity = fileService.getByIdChecked(resume.getResumeFileId());
            byte[] bytes = fileService.downloadBytes(resume.getResumeFileId());
            if (bytes == null || bytes.length == 0) {
                return fail("简历附件下载失败");
            }
            ResumeParsedData parsed = resumeParserService.parse(bytes, fileEntity.getOriginalName(), null);
            String text = parsed == null ? null : parsed.getRawText();
            if (text == null || text.isBlank()) {
                return fail("未能从简历中提取文本，深度分析失败");
            }

            AiMessage answer = chatLanguageModel.generate(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage("【系统技能标签库】\n" + buildTagCatalog() + "\n\n请对以下候选人简历进行深度评估并打分。\n\n简历原文：\n" + text)
            ).content();
            ResumeDeepAnalysisVO result = parseJson(answer.text());
            if (result == null) {
                vo.setSuccess(true);
                vo.setSummary(answer.text() == null ? "未获取到模型输出" : answer.text().strip());
                vo.setStrengths(new ArrayList<>());
                vo.setWeaknesses(new ArrayList<>());
                vo.setSuggestions(new ArrayList<>());
                vo.setTagMatch(new ArrayList<>());
                result = vo;
            }
            saveScore(resume, result);
            return result;
        } catch (Exception e) {
            log.error("简历深度分析异常 resumeId={}", resumeId, e);
            return fail("深度分析失败：" + e.getMessage());
        }
    }

    @Override
    public ResumeDeepAnalysisVO getScore(Long resumeId) {
        try {
            Resume resume = resumeService.getByIdChecked(resumeId);
            if (resume.getScoreDetail() == null || resume.getScoreDetail().isBlank()) {
                ResumeDeepAnalysisVO vo = new ResumeDeepAnalysisVO();
                vo.setSuccess(false);
                vo.setSummary("该简历尚未进行深度 AI 分析");
                return vo;
            }
            return objectMapper.readValue(resume.getScoreDetail(), ResumeDeepAnalysisVO.class);
        } catch (Exception e) {
            log.error("查询简历评分失败 resumeId={}", resumeId, e);
            ResumeDeepAnalysisVO vo = new ResumeDeepAnalysisVO();
            vo.setSuccess(false);
            vo.setSummary("查询评分失败：" + e.getMessage());
            return vo;
        }
    }

    /**
     * 评分结果落库：综合分写 match_score，完整明细序列化为 JSON 写 score_detail，供随时回看。
     */
    private void saveScore(Resume resume, ResumeDeepAnalysisVO vo) {
        try {
            resume.setMatchScore(vo.getScore() == null ? null : BigDecimal.valueOf(vo.getScore()));
            resume.setScoreDetail(objectMapper.writeValueAsString(vo));
            resumeService.updateById(resume);
        } catch (Exception e) {
            log.warn("简历评分落库失败 resumeId={}", resume.getId(), e);
        }
    }

    private ResumeDeepAnalysisVO fail(String message) {
        ResumeDeepAnalysisVO vo = new ResumeDeepAnalysisVO();
        vo.setSuccess(false);
        vo.setSummary(message);
        vo.setStrengths(new ArrayList<>());
        vo.setWeaknesses(new ArrayList<>());
        vo.setSuggestions(new ArrayList<>());
        vo.setTagMatch(new ArrayList<>());
        return vo;
    }

    /**
     * 从启用的能力标签中按分类构建技能标签库文本，供大模型做技能匹配打分。
     */
    private String buildTagCatalog() {
        List<AbilityTag> tags;
        try {
            tags = abilityTagService.list();
        } catch (Exception e) {
            log.warn("获取能力标签库失败，打分将缺少技能匹配维度", e);
            tags = new ArrayList<>();
        }
        Map<String, List<String>> byCategory = tags.stream()
                .filter(t -> Objects.equals(t.getStatus(), 1))
                .filter(t -> t.getTagName() != null && !t.getTagName().isBlank())
                .collect(Collectors.groupingBy(
                        t -> t.getTagCategory() == null || t.getTagCategory().isBlank() ? "其他" : t.getTagCategory(),
                        LinkedHashMap::new,
                        Collectors.mapping(AbilityTag::getTagName, Collectors.toList())));
        if (byCategory.isEmpty()) {
            return "（暂无技能标签库配置）";
        }
        StringBuilder sb = new StringBuilder();
        byCategory.forEach((category, names) ->
                sb.append("- ").append(category).append("：").append(String.join("、", names)).append("\n"));
        return sb.toString();
    }

    private ResumeDeepAnalysisVO parseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            String cleaned = text.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("```[a-zA-Z]*", "").replaceFirst("```$", "").strip();
            }
            JsonNode node = objectMapper.readTree(cleaned);
            ResumeDeepAnalysisVO vo = new ResumeDeepAnalysisVO();
            vo.setSuccess(true);
            vo.setSummary(node.path("summary").asText());
            vo.setStrengths(toList(node.path("strengths")));
            vo.setWeaknesses(toList(node.path("weaknesses")));
            vo.setMatchAnalysis(node.path("matchAnalysis").asText());
            vo.setSuggestions(toList(node.path("suggestions")));
            JsonNode scoreNode = node.path("score");
            vo.setScore(scoreNode.canConvertToInt() ? scoreNode.asInt() : null);
            vo.setTagMatch(parseTagMatch(node.path("tagMatch")));
            return vo;
        } catch (Exception e) {
            log.warn("简历深度分析结果 JSON 解析失败，降级为纯文本", e);
            return null;
        }
    }

    private List<ResumeTagMatchVO> parseTagMatch(JsonNode node) {
        List<ResumeTagMatchVO> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode it : node) {
                ResumeTagMatchVO m = new ResumeTagMatchVO();
                m.setCategory(it.path("category").asText());
                JsonNode s = it.path("score");
                m.setScore(s.canConvertToInt() ? s.asInt() : null);
                m.setMatched(toList(it.path("matched")));
                m.setMissing(toList(it.path("missing")));
                list.add(m);
            }
        }
        return list;
    }

    private List<String> toList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode it : node) {
                list.add(it.asText());
            }
        }
        return list;
    }
}