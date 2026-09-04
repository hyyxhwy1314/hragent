package org.example.hragent.agent.nodes;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.state.HrAgentState;
import org.example.hragent.agent.state.ToolCallRecord;
import org.example.hragent.agent.tools.HrAgentAiServiceFactory;
import org.example.hragent.agent.tools.HrBusinessTools;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具执行节点（Action）
 * <p>
 * ReAct 循环图中的"动作"节点。消费 {@link HrAgentState#TOOL_CALLS_KEY} 中模型下发的
 * 工具调用请求，逐个执行并将结果以 {@link ToolExecutionResultMessage} 回填到会话
 * ChatMemory，供下一轮模型推理使用（形成 model→action→model 反馈闭环）：
 * <ol>
 *     <li>取出待执行的工具调用请求列表</li>
 *     <li>基于工具名称定位 HrBusinessTools 中对应的 @Tool 方法并执行</li>
 *     <li>把「工具名 + 结果」写入 ChatMemory（tool request/result 配对）</li>
 *     <li>清空本次待执行列表、累积结果文本，回到 model 节点继续决策</li>
 * </ol>
 */
@Component
public class ToolExecuteNode implements NodeAction<HrAgentState> {

    private final HrBusinessTools hrBusinessTools;
    private final HrAgentAiServiceFactory chatMemoryFactory;

    public ToolExecuteNode(HrBusinessTools hrBusinessTools,
                           HrAgentAiServiceFactory chatMemoryFactory) {
        this.hrBusinessTools = hrBusinessTools;
        this.chatMemoryFactory = chatMemoryFactory;
    }

    @Override
    public Map<String, Object> apply(HrAgentState state) {
        Long memoryId = chatMemoryIdOf(state);
        ChatMemory memory = chatMemoryFactory.getChatMemory(memoryId);
        List<ToolCallRecord> calls = state.toolCalls();

        // 逐个执行工具，结果以 ToolExecutionResultMessage 回填会话记忆
        StringBuilder summary = new StringBuilder();
        for (ToolCallRecord call : calls) {
            ToolExecutionRequest request = call.toRequest();
            String result = execute(request, memoryId);
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
     * 执行单个工具调用请求
     *
     * @param request  模型下发的工具调用请求
     * @param memoryId 会话记忆 ID（透传给可能使用 @MemoryId 的工具方法）
     */
    private String execute(ToolExecutionRequest request, Long memoryId) {
        try {
            // DefaultToolExecutor 依据 request 自动定位并反射调用目标方法
            DefaultToolExecutor executor = new DefaultToolExecutor(hrBusinessTools, request);
            return executor.execute(request, memoryId);
        } catch (Exception e) {
            return "执行失败：" + e.getMessage();
        }
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