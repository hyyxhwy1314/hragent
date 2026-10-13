package org.example.hragent.agent.nodes;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.mcp.FeishuMcpToolProvider;
import org.example.hragent.agent.routing.ToolRegistry;
import org.example.hragent.agent.routing.ToolRouter;
import org.example.hragent.agent.routing.ToolRoutingMetrics;
import org.example.hragent.agent.routing.ToolRoutingProperties;
import org.example.hragent.agent.state.HrAgentState;
import org.example.hragent.agent.state.ToolCallRecord;
import org.example.hragent.agent.tools.HrAgentAiServiceFactory;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.utils.CurrentUserService.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具执行节点（Action）
 * <p>
 * ReAct 循环图中的"动作"节点。消费 {@link HrAgentState#TOOL_CALLS_KEY} 中模型下发的
 * 工具调用请求，逐个执行并将结果以 {@link ToolExecutionResultMessage} 回填到会话
 * ChatMemory，供下一轮模型推理使用（形成 model→action→model 反馈闭环）：
 * <ol>
 *     <li>取出待执行的工具调用请求列表</li>
 *     <li>基于工具名称定位所属 Bean 中对应的 @Tool 方法并执行</li>
 *     <li>把「工具名 + 结果」写入 ChatMemory（tool request/result 配对）</li>
 *     <li>清空本次待执行列表、累积结果文本，回到 model 节点继续决策</li>
 * </ol>
 */
@Component
public class ToolExecuteNode implements NodeAction<HrAgentState> {

    private final ToolRegistry toolRegistry;
    private final HrAgentAiServiceFactory chatMemoryFactory;
    private final FeishuMcpToolProvider feishuMcpToolProvider;
    private final ToolRouter toolRouter;
    private final ToolRoutingProperties routingProperties;
    private final ToolRoutingMetrics routingMetrics;

    /** 单条工具结果回填会话记忆的最大字符数（超出截断，防止长列表撑爆 token 预算） */
    private final int maxResultChars;

    public ToolExecuteNode(ToolRegistry toolRegistry,
                           HrAgentAiServiceFactory chatMemoryFactory,
                           FeishuMcpToolProvider feishuMcpToolProvider,
                           ToolRouter toolRouter,
                           ToolRoutingProperties routingProperties,
                           ToolRoutingMetrics routingMetrics,
                           @Value("${hragent.agent.memory.max-tool-result-chars:3000}") int maxResultChars) {
        this.toolRegistry = toolRegistry;
        this.chatMemoryFactory = chatMemoryFactory;
        this.feishuMcpToolProvider = feishuMcpToolProvider;
        this.toolRouter = toolRouter;
        this.routingProperties = routingProperties;
        this.routingMetrics = routingMetrics;
        this.maxResultChars = maxResultChars;
    }

    @Override
    public Map<String, Object> apply(HrAgentState state) {
        boolean loginRestored = restoreLoginContext(state);
        try {
            return doApply(state);
        } finally {
            if (loginRestored) {
                CurrentUserService.clear();
            }
        }
    }

    private Map<String, Object> doApply(HrAgentState state) {
        Long memoryId = chatMemoryIdOf(state);
        ChatMemory memory = chatMemoryFactory.getChatMemory(memoryId);
        List<ToolCallRecord> calls = state.toolCalls();

        // 逐个执行工具，结果以 ToolExecutionResultMessage 回填会话记忆
        StringBuilder summary = new StringBuilder();
        Set<String> routedToolNames = new HashSet<>(state.routedToolNames());
        ToolRouter.Context routeContext = routeContext(state);
        for (ToolCallRecord call : calls) {
            ToolExecutionRequest request = call.toRequest();
            String result = truncate(guardAndExecute(request, memoryId, routedToolNames, routeContext));
            memory.add(new ToolExecutionResultMessage(request.id(), request.name(), result));
            summary.append("[工具 ").append(request.name()).append("] ").append(result).append("\n");
        }

        // 工具执行完成：清空本次待执行列表并累积结果，回到 model 节点继续决策
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.TOOL_CALLS_KEY, List.<ToolCallRecord>of());
        updates.put(HrAgentState.TOOL_RESULTS_KEY, summary.toString().trim());
        // 累计工具调用次数（多轮工具循环累加）
        updates.put(HrAgentState.TOOL_CALL_COUNT_KEY, state.toolCallCount() + calls.size());
        return updates;
    }

    /**
     * 在节点执行线程重建登录上下文：stream 模式下图节点运行在 langgraph4j 内部线程，
     * ThreadLocal 登录态不会自动传递。仅当当前线程无登录态时重建
     * （同步 invoke 模式下节点线程即请求线程，不干预、不清理）。
     */
    private boolean restoreLoginContext(HrAgentState state) {
        if (CurrentUserService.get() != null) {
            return false;
        }
        Object user = state.value(HrAgentState.CURRENT_USER_KEY).orElse(null);
        if (user instanceof CurrentUser currentUser) {
            CurrentUserService.set(currentUser);
            return true;
        }
        return false;
    }

    /**
     * 执行前鉴权（分层第 6 层）：
     * <ol>
     *   <li>拒绝模型下发的「候选外工具」——防止幻觉或提示注入越出本次授权范围</li>
     *   <li>二次校验角色可见性与黑名单——与路由层同源，做纵深防御</li>
     * </ol>
     */
    private String guardAndExecute(ToolExecutionRequest request, Long memoryId,
                                   Set<String> routedToolNames, ToolRouter.Context routeContext) {
        if (!routedToolNames.isEmpty() && !routedToolNames.contains(request.name())) {
            routingMetrics.recordOutOfCandidateRejected();
            return "工具 [" + request.name() + "] 不在本次可用范围内，已拒绝执行。";
        }
        if (!toolRouter.isAllowed(request.name(), routeContext)) {
            routingMetrics.recordRoleDenied();
            return "当前账号无权调用工具 [" + request.name() + "]。";
        }
        return execute(request, memoryId);
    }

    /** 构建执行前鉴权上下文：角色取自状态中的登录用户快照 */
    private ToolRouter.Context routeContext(HrAgentState state) {
        Object user = state.value(HrAgentState.CURRENT_USER_KEY).orElse(null);
        String role = user instanceof CurrentUser currentUser ? currentUser.getRole() : null;
        return ToolRouter.Context.of(routingProperties.getEnvironment(), role);
    }

    /**
     * 执行单个工具调用请求：飞书 MCP 工具走 MCP 通道，其余走本地 @Tool 反射调用
     *
     * @param request  模型下发的工具调用请求
     * @param memoryId 会话记忆 ID（透传给可能使用 @MemoryId 的工具方法）
     */
    private String execute(ToolExecutionRequest request, Long memoryId) {
        // 飞书 MCP 工具（如 create-doc/fetch-doc）路由到 MCP 执行器
        if (feishuMcpToolProvider.handles(request.name())) {
            return feishuMcpToolProvider.execute(request);
        }
        try {
            // 定位承载该 @Tool 方法的 Bean，再交由 DefaultToolExecutor 反射调用
            Object owner = toolRegistry.ownerOf(request.name()).orElse(null);
            if (owner == null) {
                return "未知工具：" + request.name();
            }
            DefaultToolExecutor executor = new DefaultToolExecutor(owner, request);
            return executor.execute(request, memoryId);
        } catch (Exception e) {
            return "执行失败：" + e.getMessage();
        }
    }

    /**
     * 对工具返回结果做长度截断（保留开头关键信息 + 截断提示）
     */
    private String truncate(String result) {
        if (result == null || result.length() <= maxResultChars) {
            return result;
        }
        // 截到 maxResultChars 长度并避免切断 UTF-16 代理对（emoji 等）
        String head = result.substring(0, Math.max(0, maxResultChars - 30));
        while (!head.isEmpty() && Character.isHighSurrogate(head.charAt(head.length() - 1))) {
            head = head.substring(0, head.length() - 1);
        }
        return head + "\n...(结果过长，已截断，如需完整数据请单独查询)";
    }

    private Long chatMemoryIdOf(HrAgentState state) {
        Long id = state.chatMemoryId();
        if (id == null) {
            String sessionId = state.sessionId();
            id = sessionId != null ? (long) sessionId.hashCode() : 0L;
        }
        return id;
    }
}