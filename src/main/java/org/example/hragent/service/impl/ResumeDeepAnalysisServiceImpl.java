package org.example.hragent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.FileEntity;
import org.example.hragent.entity.Resume;
import org.example.hragent.service.FileService;
import org.example.hragent.service.ResumeParserService;
import org.example.hragent.service.ResumeService;
import org.example.hragent.service.ResumeDeepAnalysisService;
import org.example.hragent.vo.ResumeDeepAnalysisVO;
import org.example.hragent.vo.ResumeParsedData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
              "suggestions": ["招聘/面试建议1"]
            }

            要求：
            1. strengths 给出 3-5 条最突出的优势；weaknesses 给出 2-4 条，若无明显短板可为空数组。
            2. 结合简历中的教育背景、工作经历、技能与项目经验进行客观分析，避免空泛套话。
            3. 全部使用中文。
            """;

    private final ResumeService resumeService;
    private final FileService fileService;
    private final ResumeParserService resumeParserService;
    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public ResumeDeepAnalysisServiceImpl(ResumeService resumeService,
                                         FileService fileService,
                                         ResumeParserService resumeParserService,
                                         ChatLanguageModel chatLanguageModel,
                                         ObjectMapper objectMapper) {
        this.resumeService = resumeService;
        this.fileService = fileService;
        this.resumeParserService = resumeParserService;
        this.chatLanguageModel = chatLanguageModel;
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
                    new UserMessage("请对以下候选人简历进行深度评估。\n\n简历原文：\n" + text)
            ).content();
            ResumeDeepAnalysisVO parsedVo = parseJson(answer.text());
            if (parsedVo != null) {
                return parsedVo;
            }
            vo.setSuccess(true);
            vo.setSummary(answer.text() == null ? "未获取到模型输出" : answer.text().strip());
            vo.setStrengths(new ArrayList<>());
            vo.setWeaknesses(new ArrayList<>());
            vo.setSuggestions(new ArrayList<>());
            return vo;
        } catch (Exception e) {
            log.error("简历深度分析异常 resumeId={}", resumeId, e);
            return fail("深度分析失败：" + e.getMessage());
        }
    }

    private ResumeDeepAnalysisVO fail(String message) {
        ResumeDeepAnalysisVO vo = new ResumeDeepAnalysisVO();
        vo.setSuccess(false);
        vo.setSummary(message);
        vo.setStrengths(new ArrayList<>());
        vo.setWeaknesses(new ArrayList<>());
        vo.setSuggestions(new ArrayList<>());
        return vo;
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
            return vo;
        } catch (Exception e) {
            log.warn("简历深度分析结果 JSON 解析失败，降级为纯文本", e);
            return null;
        }
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