package org.example.hragent.agent.nodes;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.state.HrAgentState;
import org.example.hragent.agent.state.ToolCallRecord;
import org.example.hragent.agent.tools.HrAgentAiServiceFactory;
import org.example.hragent.agent.tools.HrBusinessTools;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型推理节点（Think）
 * <p>
 * ReAct 循环图中的"思考"节点。每次被调用只做<b>一次</b> LLM 推理：
 * <ol>
 *     <li>首轮（iteration=0）把用户消息写入会话 ChatMemory</li>
 *     <li>将 [系统提示词 + 会话历史] 发给模型，注入全部 @Tool 规格</li>
 *     <li>若模型要求调用工具：把 ToolExecutionRequest 下发到 TOOL_CALLS 状态，路由到 action</li>
 *     <li>若模型给出最终回答：写入 MESSAGES 并清空 TOOL_CALLS，路由到 answer</li>
 *     <li>超过 {@link #MAX_TOOL_CALLS} 轮强制收尾，防止死循环</li>
 * </ol>
 */
@Component
public class ModelThinkNode implements NodeAction<HrAgentState> {

    /** 单轮任务最多允许的工具调用轮次 */
    private static final int MAX_TOOL_CALLS = 8;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的 HR 人力资源智能助手。你可以帮助用户：
            - 查询员工、岗位、简历、绩效、培训课程等信息
            - 登记候选人简历，发起入职审批流程
            - 发起离职、调岗等审批流程
            - 查询流程实例和审批轨迹，处理待办审批（通过/拒绝）
            - 回答人力资源政策相关问题

            请根据用户的问题，调用合适的工具来获取信息并给出准确的回答。
            如果用户的问题不需要调用工具（如普通闲聊），直接回复即可。
            回答请使用中文。

            【入职流程的使用规则（重要）】
            入职流程与候选人简历强关联：流程的业务ID(bizId)必须是简历ID，审批通过后系统会依据简历自动创建员工记录。
            且仅「录用」状态的简历才能发起入职。因此发起入职前，请按以下顺序处理：
            1. 先用「查询候选人简历信息」确认系统中是否已有该候选人的简历及当前状态；
            2. 若尚无简历，先调用「登记候选人简历」创建简历并拿到简历ID（新简历默认状态为待筛选）；
            3. 若简历状态不是「录用」，先调用「录用候选人简历」将其标记为录用（否则发起入职会被拦截）；
            4. 再调用「为候选人发起入职审批流程」，参数需包含候选人姓名/简历ID 和 用人部门主管姓名；
            5. 发起后可通过「查询流程实例」「查询审批轨迹」「查询当前登录人的待办审批任务」跟进，审批人可在待办中「完成审批任务」。
            """;

    private final ChatLanguageModel chatModel;
    private final HrAgentAiServiceFactory chatMemoryFactory;
    private final List<ToolSpecification> toolSpecifications;

    public ModelThinkNode(ChatLanguageModel chatModel,
                          HrAgentAiServiceFactory chatMemoryFactory,
                          HrBusinessTools hrBusinessTools) {
        this.chatModel = chatModel;
        this.chatMemoryFactory = chatMemoryFactory;
        this.toolSpecifications = ToolSpecifications.toolSpecificationsFrom(hrBusinessTools);
    }

    @Override
    public Map<String, Object> apply(HrAgentState state) {
        Long memoryId = chatMemoryIdOf(state);
        ChatMemory memory = chatMemoryFactory.getChatMemory(memoryId);

        String userQuery = lastUserMessage(state);
        if (userQuery.isEmpty()) {
            return error("未找到用户消息，请稍后重试", memoryId);
        }

        int iteration = state.currentIteration();
        try {
            // 首轮：把本次用户消息写入会话记忆
            if (iteration == 0) {
                memory.add(UserMessage.userMessage(userQuery));
            }

            // [系统提示词 + 会话历史] ->模型，注入全部工具规格
            List<ChatMessage> prompt = new ArrayList<>();
            prompt.add(new SystemMessage(SYSTEM_PROMPT));
            prompt.addAll(memory.messages());
            Response<AiMessage> response = chatModel.generate(prompt, toolSpecifications);
            AiMessage aiMessage = response.content();
            // 模型输出写入记忆，保证多轮/工具循环上下文连续
            memory.add(aiMessage);

            if (aiMessage.hasToolExecutionRequests()) {
                if (iteration >= MAX_TOOL_CALLS) {
                    // 达到轮次上限，强制收尾
                    String finalText = "我已经进行了多轮工具查询，但仍无法获取完整信息，请提供更明确的条件（如精确姓名、工号或简历ID）后重试。";
                    memory.add(AiMessage.from(finalText));
                    return routeAnswer(state, finalText, iteration);
                }
                // 路由到 action 去执行工具
                Map<String, Object> updates = new HashMap<>();
                updates.put(HrAgentState.ITERATION_KEY, iteration + 1);
                updates.put(HrAgentState.TOOL_CALLS_KEY, aiMessage.toolExecutionRequests().stream()
                        .map(ToolCallRecord::from)
                        .toList());
                return updates;
            }

            String finalText = aiMessage.text() == null ? "已处理完成。" : aiMessage.text();
            return routeAnswer(state, finalText, iteration);
        } catch (Exception e) {
            return error("AI 推理失败：" + e.getMessage(), memoryId);
        }
    }

    /**
     * 路由到最终回答：写入 MESSAGES、清空 TOOL_CALLS（使条件边走向 answer）
     */
    private Map<String, Object> routeAnswer(HrAgentState state, String text, int iteration) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.MESSAGES_KEY, List.of(HrAgentState.ASSISTANT_PREFIX + text));
        updates.put(HrAgentState.ITERATION_KEY, iteration + 1);
        updates.put(HrAgentState.TOOL_CALLS_KEY, List.<ToolCallRecord>of());
        updates.put(HrAgentState.TOOL_RESULTS_KEY, text);
        return updates;
    }

    private Map<String, Object> error(String message, Long memoryId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.ERROR_MESSAGE_KEY, message);
        updates.put(HrAgentState.MESSAGES_KEY, List.of(HrAgentState.ASSISTANT_PREFIX + message));
        updates.put(HrAgentState.TOOL_CALLS_KEY, List.<ToolCallRecord>of());
        updates.put(HrAgentState.TOOL_RESULTS_KEY, "错误：" + message);
        return updates;
    }

    private Long chatMemoryIdOf(HrAgentState state) {
        Long id = state.chatMemoryId();
        if (id == null) {
            String sessionId = state.sessionId();
            id = sessionId != null ? (long) sessionId.hashCode() : 0L;
        }
        return id;
    }

    /**
     * 从状态消息中提取最后一条用户消息
     */
    private String lastUserMessage(HrAgentState state) {
        List<String> messages = state.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            String msg = messages.get(i);
            if (msg.startsWith(HrAgentState.USER_PREFIX)) {
                return msg.substring(HrAgentState.USER_PREFIX.length());
            }
        }
        return "";
    }
}