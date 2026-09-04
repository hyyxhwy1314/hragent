package org.example.hragent.agent.graph;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.example.hragent.agent.nodes.ModelThinkNode;
import org.example.hragent.agent.nodes.ToolExecuteNode;
import org.example.hragent.agent.state.HrAgentState;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * HR Agent 工作流图定义（ReAct 循环图）
 * <p>
 * 以 LangGraph4j 状态机图 + LangChain4j 模型/工具编排，适合处理需要多步工具
 * 协作的复杂任务，而非串行单通道执行：
 * <pre>
 *     START ──▶ model ──(有工具调用)──▶ action ──────▶ model ── ...
 *                    │                        ▲（循环）
 *                    └────(无工具调用)─────────┴──────────────────────▶ END
 * </pre>
 * <ul>
 *     <li><b>model</b>（ModelThinkNode）— 首次把用户消息写入会话记忆，调用 LLM 推理并动态注入相关 @Tool 规格（按用户意图过滤，节省 token）：
 *         模型要求调工具则下发 TOOL_CALLS 走 action，给出最终回答则写 MESSAGES 走 END。</li>
 *     <li><b>action</b>（ToolExecuteNode）— 执行工具调用并把结果以 ToolExecutionResultMessage 回填
 *         会话记忆，清空 TOOL_CALLS 后回到 model 继续决策。</li>
 * </ul>
 * model 之后的<b>条件边</b>判断 TOOL_CALLS 是否为空：为空（已有最终回答/错误）→ END，
 * 非空（模型仍在下发工具请求）→ action。模型单轮最多允许 {@code MAX_TOOL_CALLS} 轮工具循环，
 * 防止死循环。
 */
@Component
public class HrAgentGraph {

    private final ModelThinkNode modelThinkNode;
    private final ToolExecuteNode toolExecuteNode;

    public HrAgentGraph(ModelThinkNode modelThinkNode,
                        ToolExecuteNode toolExecuteNode) {
        this.modelThinkNode = modelThinkNode;
        this.toolExecuteNode = toolExecuteNode;
    }

    /**
     * 构建并编译 HR Agent ReAct 循环工作流图
     */
    public StateGraph<HrAgentState> buildGraph() throws GraphStateException {
        StateGraph<HrAgentState> workflow = new StateGraph<>(HrAgentState.SCHEMA, HrAgentState::new);

        // 添加节点
        workflow.addNode("model", AsyncNodeAction.node_async(modelThinkNode));
        workflow.addNode("action", AsyncNodeAction.node_async(toolExecuteNode));

        // 开始 -> 模型推理
        workflow.addEdge(START, "model");

        // model 之后条件路由：有工具调用 -> action；无（已生成最终回答/错误）-> END
        workflow.addConditionalEdges(
                "model",
                AsyncEdgeAction.edge_async(state -> state.toolCalls().isEmpty() ? END : "action"),
                Map.of(END, END, "action", "action"));

        // action 执行完回到模型，形成 model->action->model 循环闭包
        workflow.addEdge("action", "model");

        return workflow;
    }
}