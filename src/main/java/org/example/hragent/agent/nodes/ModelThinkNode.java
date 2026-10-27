package org.example.hragent.agent.nodes;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.routing.ToolPlanningStrategy;
import org.example.hragent.agent.routing.ToolRouter;
import org.example.hragent.agent.routing.ToolRoutingProperties;
import org.example.hragent.agent.state.HrAgentState;
import org.example.hragent.agent.state.ToolCallRecord;
import org.example.hragent.agent.tools.HrAgentAiServiceFactory;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.utils.CurrentUserService.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private static final Logger log = LoggerFactory.getLogger(ModelThinkNode.class);

    private static final String SYSTEM_PROMPT = """
            你是一个专业的 HR 人力资源智能助手。负责：查询员工/岗位/简历/绩效/培训课程等；
            登记简历、发起入职/离职/调岗审批；查询流程实例与审批轨迹；处理待办审批；回答 HR 政策问题。

            请根据用户的当前诉求，结合可用工具的说明自主判断并调用合适的工具完成任务。
            遵循最小步骤原则：能一步完成就不要拆成多步，避免不必要的工具调用以节省开销。
            判定工具所需的关键参数（如姓名、简历ID、主管姓名）需从当前对话上下文与用户答复中提取，
            信息不足时才向用户询问，得到一个关键答案后应继续推进任务，不要重复提问。
            多步骤流程（如发起入职需要先确认候选人有简历且状态为录用）应依据工具返回的提示自主补全。
            若用户问题无需调用工具（如闲聊），直接简洁作答即可。回答一律使用中文。
            涉及公司政策、制度、考勤休假、薪酬福利、合规廉洁、举报与奖惩等咨询时，必须先调用
            hrKnowledgeQA 检索制度原文，再依据返回条款作答；未检索到相关条款时应明确说明，
            不得凭常识编造。只有用户打招呼或询问“你能做什么”时，才输出欢迎引导语，
            用户提出具体问题时不得用引导菜单代替回答。
            若用户要求把内容沉淀为飞书云文档、读取/搜索/评论飞书云文档（工具名称形如 create-doc、
            fetch-doc、update-doc、list-docs、get-comments、add-comments），仅当用户明确提到
            飞书/云文档/知识库场景时才调用这些飞书 MCP 工具。
            """;

    private final ChatLanguageModel chatModel;
    private final HrAgentAiServiceFactory chatMemoryFactory;
    private final ToolRouter toolRouter;
    private final ToolPlanningStrategy toolPlanning;
    private final ToolRoutingProperties routingProperties;

    public ModelThinkNode(ChatLanguageModel chatModel,
                          HrAgentAiServiceFactory chatMemoryFactory,
                          ToolRouter toolRouter,
                          ToolPlanningStrategy toolPlanning,
                          ToolRoutingProperties routingProperties) {
        this.chatModel = chatModel;
        this.chatMemoryFactory = chatMemoryFactory;
        this.toolRouter = toolRouter;
        this.toolPlanning = toolPlanning;
        this.routingProperties = routingProperties;
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

            ToolRouter.Result routeResult = toolRouter.route(userQuery, routeContext(state));
            // 本地只读规划命中则跳过远程首跳，否则交回远程模型（Optional 链式，无运行时条件分支）
            return localPlan(iteration, userQuery, routeResult)
                    .map(planned -> planToolCallPath(state, memory, iteration, planned, routeResult))
                    .orElseGet(() -> remoteThinkPath(state, memory, iteration, routeResult));
        } catch (Exception e) {
            return error("AI 推理失败：" + e.getMessage(), memoryId);
        }
    }

    /**
     * 首轮尝试由本地轻量模型对只读候选工具做规划（分层第 4 层「模型精选」的本地实现）。
     * 仅首轮生效——后续轮次需依据工具结果继续决策，本地规划器拿不到该上下文；
     * 策略内部已对异常/超时/输出非法/候选外/空规划做兜底，此处只做「仅首轮」过滤。
     */
    private Optional<List<ToolCallRecord>> localPlan(int iteration, String userQuery, ToolRouter.Result routeResult) {
        return Optional.of(iteration)
                .filter(i -> i == 0)
                .flatMap(i -> toolPlanning.plan(userQuery, routeResult.cards(), routeResult.domains()))
                .filter(planned -> !planned.isEmpty());
    }

    /**
     * 本地规划命中：构造与「模型下发工具调用」等价的 AiMessage 写入会话记忆，
     * 使随后回填的 {@code ToolExecutionResultMessage} 能与之配对成合法的工具消息对；
     * 直接下发 TOOL_CALLS/ROUTED_TOOLS 跳过远程首跳。本帧未发生远程调用，token 用量保持不变。
     */
    private Map<String, Object> planToolCallPath(HrAgentState state, ChatMemory memory, int iteration,
                                                 List<ToolCallRecord> planned, ToolRouter.Result routeResult) {
        memory.add(AiMessage.from(planned.stream().map(ToolCallRecord::toRequest).toList()));
        log.debug("本地只读规划命中，跳过远程首跳 工具={}", planned.stream().map(ToolCallRecord::name).toList());
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.ITERATION_KEY, iteration + 1);
        // 下发本轮候选工具名，执行节点据此拒绝候选外调用
        updates.put(HrAgentState.ROUTED_TOOLS_KEY, routeResult.toolNames());
        updates.put(HrAgentState.TOOL_CALLS_KEY, planned);
        return updates;
    }

    /** 远程模型路径：按候选 Schema 自选工具或给出最终回答（原有行为不变） */
    private Map<String, Object> remoteThinkPath(HrAgentState state, ChatMemory memory, int iteration,
                                                ToolRouter.Result routeResult) {
        // [系统提示词 + 会话历史] ->模型，仅注入分层路由筛选出的少量候选工具 Schema
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(new SystemMessage(SYSTEM_PROMPT));
        prompt.addAll(memory.messages());
        Response<AiMessage> response = chatModel.generate(prompt, routeResult.specifications());
        AiMessage aiMessage = response.content();
        // 模型输出写入记忆，保证多轮/工具循环上下文连续
        memory.add(aiMessage);

        // 累计本帧 token 消耗（多帧工具循环时累加）
        int inputTokens = state.inputTokens() + usageInt(response, true);
        int outputTokens = state.outputTokens() + usageInt(response, false);

        if (!aiMessage.hasToolExecutionRequests()) {
            String finalText = aiMessage.text() == null ? "已处理完成。" : aiMessage.text();
            return routeAnswer(state, finalText, iteration, inputTokens, outputTokens);
        }
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
        // 下发本轮候选工具名，执行节点据此拒绝候选外调用
        updates.put(HrAgentState.ROUTED_TOOLS_KEY, routeResult.toolNames());
        updates.put(HrAgentState.TOOL_CALLS_KEY, aiMessage.toolExecutionRequests().stream()
                .map(ToolCallRecord::from)
                .toList());
        return updates;
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

    /**
     * 构建本次路由上下文：环境取自配置，角色取自状态中的登录用户快照
     * （stream 模式节点在内部线程执行，无 ThreadLocal 时兜底读当前线程）。
     */
    private ToolRouter.Context routeContext(HrAgentState state) {
        Object user = state.value(HrAgentState.CURRENT_USER_KEY).orElse(null);
        String role = user instanceof CurrentUser currentUser
                ? currentUser.getRole()
                : CurrentUserService.get() == null ? null : CurrentUserService.get().getRole();
        return ToolRouter.Context.of(routingProperties.getEnvironment(), role);
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