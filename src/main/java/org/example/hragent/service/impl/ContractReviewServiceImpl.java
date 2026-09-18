package org.example.hragent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.service.ContractReviewService;
import org.example.hragent.vo.ContractIssueVO;
import org.example.hragent.vo.ContractReviewVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 合同审查服务实现
 * <p>
 * 复用 AI 对话使用的 {@link ChatLanguageModel}（DeepSeek，OpenAI 兼容），
 * 在提示词中约定严格的 JSON 输出结构，审查完成后解析为结构化风险报告。
 * 解析失败时兜底为原始文本摘要，保障接口可用性。
 */
@Slf4j
@Service
public class ContractReviewServiceImpl implements ContractReviewService {

    private static final String SYSTEM_PROMPT = """
            你是一名资深的人力资源与劳动法律顾问，负责审查劳动合同、聘用协议、保密协议、
            竞业限制协议等法律文件，识别其中的潜在法律风险，并为 HR 提供务实、可落地的修改建议。

            你的输出必须严格是一个合法的 JSON 对象，不要输出任何多余的文字、解释或 Markdown 代码块。
            输出的 JSON 必须符合如下结构：
            {
              "riskLevel": "高|中|低",
              "summary": "对合同整体的总体评价与结论",
              "issues": [
                {
                  "contractClause": "涉及条款或位置（如：第三条 薪资福利 / 第八条）",
                  "issueType": "风险类型（如：违约责任 / 薪资福利 / 保密协议 / 竞业限制 / 劳动关系终止 / 其他）",
                  "severity": "高|中|低",
                  "description": "该风险点的具体描述",
                  "suggestion": "修改建议"
                }
              ]
            }

            要求：
            1. issues 至少覆盖合同中真实存在的风险点；若合同确实规范、无明显风险，issues 可为空数组，riskLevel 取“低”。
            2. 每个风险点都要给出可操作的修改建议，建议要具体，避免空泛。
            3. 用中文回答，severity 与 riskLevel 只允许“高”“中”“低”三选一。
            """;

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public ContractReviewServiceImpl(ChatLanguageModel chatLanguageModel, ObjectMapper objectMapper) {
        this.chatLanguageModel = chatLanguageModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public ContractReviewVO review(String content, String contractName) {
        ContractReviewVO vo = new ContractReviewVO();
        String name = (contractName == null || contractName.isBlank()) ? "本协议" : contractName;
        try {
            AiMessage answer = chatLanguageModel.generate(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage(String.format("请审查以下合同「%s」全文，分析潜在法律风险并给出修改建议。\n\n合同全文：\n%s", name, content))
            ).content();
            String text = answer.text();
            log.info("合同审查完成 contractName={}, 输出长度={}", name, text == null ? 0 : text.length());

            ContractReviewVO parsed = parseJson(text);
            if (parsed != null) {
                return parsed;
            }
            // 模型未输出符合结构的 JSON，降级为纯文本评价
            vo.setSuccess(true);
            vo.setSummary(text == null ? "未获取到模型输出" : text.strip());
            vo.setIssues(new ArrayList<>());
            vo.setTotalIssues(0);
            return vo;
        } catch (Exception e) {
            log.error("合同审查异常 contractName={}", name, e);
            vo.setSuccess(false);
            vo.setSummary("合同审查失败：" + e.getMessage());
            vo.setIssues(new ArrayList<>());
            vo.setTotalIssues(0);
            return vo;
        }
    }

    private ContractReviewVO parseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            // 容忍模型偶尔用 ```json ... ``` 包裹
            String cleaned = text.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("```[a-zA-Z]*", "").replaceFirst("```$", "").strip();
            }
            JsonNode node = objectMapper.readTree(cleaned);
            ContractReviewVO vo = new ContractReviewVO();
            vo.setSuccess(true);
            vo.setRiskLevel(node.path("riskLevel").asText());
            vo.setSummary(node.path("summary").asText());
            List<ContractIssueVO> issues = new ArrayList<>();
            JsonNode arr = node.path("issues");
            if (arr != null && arr.isArray()) {
                for (JsonNode it : arr) {
                    ContractIssueVO issue = new ContractIssueVO();
                    issue.setContractClause(it.path("contractClause").asText());
                    issue.setIssueType(it.path("issueType").asText());
                    issue.setSeverity(it.path("severity").asText());
                    issue.setDescription(it.path("description").asText());
                    issue.setSuggestion(it.path("suggestion").asText());
                    issues.add(issue);
                }
            }
            vo.setIssues(issues);
            vo.setTotalIssues(issues.size());
            return vo;
        } catch (Exception e) {
            log.warn("合同审查结果 JSON 解析失败，降级为纯文本", e);
            return null;
        }
    }
}