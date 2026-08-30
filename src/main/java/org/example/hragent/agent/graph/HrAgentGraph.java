package org.example.hragent.agent.graph;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.example.hragent.agent.nodes.AnswerGenerationNode;
import org.example.hragent.agent.nodes.ToolInvocationNode;
import org.example.hragent.agent.state.HrAgentState;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * HR Agent 工作流图定义
 * <p>
 * 以 LangChain4j 为核心的极简工作流：
 * <ul>
 *     <li>tool_invocation — LangChain4j AiService + ChatMemory + @Tool 一站式处理</li>
 *     <li>answer_generation — 提取最终回答</li>
 * </ul>
 * 工具调用、重试、结果校验等逻辑全部由 LangChain4j 内部自动处理，
 * LangGraph4j 只负责轻量级的流程编排和持久化衔接。
 * <p>
 * 流程图：START → tool_invocation → answer_generation → END
 */
@Component
public class HrAgentGraph {

    private final ToolInvocationNode toolInvocationNode;
    private final AnswerGenerationNode answerGenerationNode;

    public HrAgentGraph(ToolInvocationNode toolInvocationNode,
                       AnswerGenerationNode answerGenerationNode) {
        this.toolInvocationNode = toolInvocationNode;
        this.answerGenerationNode = answerGenerationNode;
    }

    /**
     * 构建并编译 HR Agent 工作流图
     */
    public StateGraph<HrAgentState> buildGraph() throws GraphStateException {
        StateGraph<HrAgentState> workflow = new StateGraph<>(HrAgentState.SCHEMA, HrAgentState::new);

        // 添加节点
        workflow.addNode("tool_invocation", AsyncNodeAction.node_async(toolInvocationNode));
        workflow.addNode("answer_generation", AsyncNodeAction.node_async(answerGenerationNode));

        // 开始 -> 工具调用（LangChain4j AiService 自动选择工具并生成回答）
        workflow.addEdge(START, "tool_invocation");

        // 工具调用 -> 答案生成（提取最终回答）
        workflow.addEdge("tool_invocation", "answer_generation");

        // 答案生成 -> 结束
        workflow.addEdge("answer_generation", END);

        return workflow;
    }
}
