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
import org.example.hragent.agent.tools.ToolFilter;
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
 *     <li>将 [系统提示词 + 会话历史] 发给模型，按用户意图动态注入相关 @Tool 规格（避免全部加载浪费 token）</li>
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
            你是一个专业的 HR 人力资源智能助手。负责：查询员工/岗位/简历/绩效/培训课程等；
            登记简历、发起入职/离职/调岗审批；查询流程实例与审批轨迹；处理待办审批；回答 HR 政策问题。

            请根据用户的当前诉求，结合可用工具的说明自主判断并调用合适的工具完成任务。
            遵循最小步骤原则：能一步完成就不要拆成多步，避免不必要的工具调用以节省开销。
            判定工具所需的关键参数（如姓名、简历ID、主管姓名）需从当前对话上下文与用户答复中提取，
            信息不足时才向用户询问，得到一个关键答案后应继续推进任务，不要重复提问。
            多步骤流程（如发起入职需要先确认候选人有简历且状态为录用）应依据工具返回的提示自主补全。
            若用户问题无需调用工具（如闲聊），直接简洁作答即可。回答一律使用中文。
            """;

    private final ChatLanguageModel chatModel;
    private final HrAgentAiServiceFactory chatMemoryFactory;
    private final List<ToolSpecification> allToolSpecifications;

    public ModelThinkNode(ChatLanguageModel chatModel,
                          HrAgentAiServiceFactory chatMemoryFactory,
                          HrBusinessTools hrBusinessTools) {
        this.chatModel = chatModel;
        this.chatMemoryFactory = chatMemoryFactory;
        this.allToolSpecifications = ToolSpecifications.toolSpecificationsFrom(hrBusinessTools);
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

            // [系统提示词 + 会话历史] ->模型，动态注入相关工具规格（按用户意图过滤）
            List<ChatMessage> prompt = new ArrayList<>();
            prompt.add(new SystemMessage(SYSTEM_PROMPT));
            prompt.addAll(memory.messages());
            List<ToolSpecification> relevantTools = ToolFilter.filter(userQuery, allToolSpecifications);
            Response<AiMessage> response = chatModel.generate(prompt, relevantTools);
            AiMessage aiMessage = response.content();
            // 模型输出写入记忆，保证多轮/工具循环上下文连续
            memory.add(aiMessage);

            // 累计本帧 token 消耗（多帧工具循环时累加）
            int frameIn = usageInt(response, true);
            int frameOut = usageInt(response, false);
            int inputTokens = state.inputTokens() + frameIn;
            int outputTokens = state.outputTokens() + frameOut;

            if (aiMessage.hasToolExecutionRequests()) {
                if (iteration >= MAX_TOOL_CALLS) {
                    // 达到轮次上限，强制收尾
                    String finalText = "我已经进行了多轮工具查询，但仍无法获取完整信息，请提供更明确的条件（如精确姓名、工号或简历ID）后重试。";
                    memory.add(AiMessage.from(finalText));
                    return routeAnswer(state, finalText, iteration, inputTokens, outputTokens);
                }
                // 路由到 action 去执行工具
                Map<String, Object> updates = new HashMap<>();
                updates.put(HrAgentState.ITERATION_KEY, iteration + 1);
                updates.put(HrAgentState.INPUT_TOKENS_KEY, inputTokens);
                updates.put(HrAgentState.OUTPUT_TOKENS_KEY, outputTokens);
                updates.put(HrAgentState.TOOL_CALLS_KEY, aiMessage.toolExecutionRequests().stream()
                        .map(ToolCallRecord::from)
                        .toList());
                return updates;
            }

            String finalText = aiMessage.text() == null ? "已处理完成。" : aiMessage.text();
            return routeAnswer(state, finalText, iteration, inputTokens, outputTokens);
        } catch (Exception e) {
            return error("AI 推理失败：" + e.getMessage(), memoryId);
        }
    }

    /**
     * 提取单次响应的 token 用量（输入或输出），model 未返回用量时为 0
     */
    private int usageInt(Response<AiMessage> response, boolean input) {
        try {
            var usage = response.tokenUsage();
            if (usage == null) return 0;
            Integer tokens = input ? usage.inputTokenCount() : usage.outputTokenCount();
            return tokens == null ? 0 : tokens;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 路由到最终回答：写入 MESSAGES、清空 TOOL_CALLS（使条件边走向 answer）
     */
    private Map<String, Object> routeAnswer(HrAgentState state, String text, int iteration,
                                           int inputTokens, int outputTokens) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.MESSAGES_KEY, List.of(HrAgentState.ASSISTANT_PREFIX + text));
        updates.put(HrAgentState.ITERATION_KEY, iteration + 1);
        updates.put(HrAgentState.TOOL_CALLS_KEY, List.<ToolCallRecord>of());
        updates.put(HrAgentState.TOOL_RESULTS_KEY, text);
        updates.put(HrAgentState.INPUT_TOKENS_KEY, inputTokens);
        updates.put(HrAgentState.OUTPUT_TOKENS_KEY, outputTokens);
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