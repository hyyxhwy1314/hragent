package org.example.hragent.agent.graph;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.example.hragent.agent.nodes.AnswerGenerationNode;
import org.example.hragent.agent.nodes.IntentRecognitionNode;
import org.example.hragent.agent.nodes.ResultValidationNode;
import org.example.hragent.agent.nodes.RetryHandlerNode;
import org.example.hragent.agent.nodes.ToolInvocationNode;
import org.example.hragent.agent.state.HrAgentState;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * HR Agent 工作流图定义
 * 通过节点和边定义 HR Agent 的工作流结构
 */
@Component
public class HrAgentGraph {
    
    private final IntentRecognitionNode intentRecognitionNode;
    private final ToolInvocationNode toolInvocationNode;
    private final ResultValidationNode resultValidationNode;
    private final RetryHandlerNode retryHandlerNode;
    private final AnswerGenerationNode answerGenerationNode;
    
    public HrAgentGraph(IntentRecognitionNode intentRecognitionNode,
                       ToolInvocationNode toolInvocationNode,
                       ResultValidationNode resultValidationNode,
                       RetryHandlerNode retryHandlerNode,
                       AnswerGenerationNode answerGenerationNode) {
        this.intentRecognitionNode = intentRecognitionNode;
        this.toolInvocationNode = toolInvocationNode;
        this.resultValidationNode = resultValidationNode;
        this.retryHandlerNode = retryHandlerNode;
        this.answerGenerationNode = answerGenerationNode;
    }
    
    /**
     * 构建并编译 HR Agent 工作流图
     */
    public StateGraph<HrAgentState> buildGraph() throws GraphStateException {
        // 使用 HR Agent 状态 schema 创建状态图
        StateGraph<HrAgentState> workflow = new StateGraph<>(HrAgentState.SCHEMA, HrAgentState::new);
        
        // 添加节点
        workflow.addNode("intent_recognition", AsyncNodeAction.node_async(intentRecognitionNode));
        workflow.addNode("tool_invocation", AsyncNodeAction.node_async(toolInvocationNode));
        workflow.addNode("result_validation", AsyncNodeAction.node_async(resultValidationNode));
        workflow.addNode("retry_handler", AsyncNodeAction.node_async(retryHandlerNode));
        workflow.addNode("answer_generation", AsyncNodeAction.node_async(answerGenerationNode));
        
        // 定义工作流的边
        
        // 开始 -> 意图识别
        workflow.addEdge(START, "intent_recognition");
        
        // 意图识别 -> 条件路由
        workflow.addConditionalEdges(
            "intent_recognition",
            AsyncEdgeAction.edge_async(this::routeAfterIntent),
            java.util.Map.of(
                "tool_invocation", "tool_invocation",
                "error_handler", "answer_generation"
            )
        );
        
        // 工具调用 -> 结果校验
        workflow.addEdge("tool_invocation", "result_validation");
        
        // 结果校验 -> 条件路由
        workflow.addConditionalEdges(
            "result_validation",
            AsyncEdgeAction.edge_async(this::routeAfterValidation),
            java.util.Map.of(
                "answer_generation", "answer_generation",
                "retry_tool", "retry_handler",
                "error_handler", "answer_generation"
            )
        );
        
        // 重试处理 -> 工具调用（重试次数 +1）
        workflow.addEdge("retry_handler", "tool_invocation");
        
        // 答案生成 -> 结束
        workflow.addEdge("answer_generation", END);
        
        return workflow;
    }
    
    /**
     * 意图识别后的条件路由
     * 决定是进入工具调用还是进行错误处理
     */
    private String routeAfterIntent(HrAgentState state) {
        // 检查意图识别过程中是否出现错误
        if (state.hasError()) {
            return "error_handler";
        }
        
        // 检查是否成功识别出意图
        if (state.intent() != null && !state.intent().isEmpty()) {
            return "tool_invocation";
        }
        
        // 意图缺失时默认进入错误处理
        return "error_handler";
    }
    
    /**
     * 结果校验后的条件路由
     * 决定是进入答案生成、重试工具调用还是进行错误处理
     */
    private String routeAfterValidation(HrAgentState state) {
        // 校验通过
        if (state.isValidated()) {
            return "answer_generation";
        }
        
        // 可重试工具调用（最多重试 3 次）
        if (state.retryCount() < 3) {
            return "retry_handler";
        }
        
        // 达到最大重试次数，进入错误处理
        return "error_handler";
    }
}
