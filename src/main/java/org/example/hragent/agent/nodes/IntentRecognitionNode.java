package org.example.hragent.agent.nodes;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.state.AgentIntent;
import org.example.hragent.agent.state.HrAgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图识别节点
 * 从用户输入消息中识别用户意图
 */
@Component
public class IntentRecognitionNode implements NodeAction<HrAgentState> {
    
    private final ChatLanguageModel chatModel;
    
    // 意图分类提示词
    private static final String INTENT_CLASSIFICATION_PROMPT = """
        你是一个HR意图分类器。分析用户的消息并分类为以下意图之一：
        
        1. query_employee_info - 查询指定员工信息
        2. query_employee_list - 查询员工列表
        3. query_job_info - 查询指定岗位信息
        4. query_job_list - 查询岗位列表
        5. query_resume_info - 查询指定简历信息
        6. query_resume_match - 查询简历匹配结果
        7. start_onboarding_process - 发起入职流程
        8. start_resignation_process - 发起离职流程
        9. start_transfer_process - 发起调岗流程
        10. query_process_status - 查询流程状态
        11. query_performance_result - 查询绩效结果
        12. generate_performance_report - 生成绩效报告
        13. generate_training_plan - 生成培训计划
        14. query_training_courses - 查询培训课程
        15. hr_knowledge_qa - HR知识问答
        16. general_conversation - 一般对话
        17. unknown - 未知意图
        
        用户消息："%s"
        
        仅返回意图代码（例如："query_employee_info"）。如果不确定意图，返回"unknown"。
        """;
    
    public IntentRecognitionNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }
    
    @Override
    public Map<String, Object> apply(HrAgentState state) {
        try {
            List<String> messages = state.messages();
            if (messages.isEmpty()) {
                return handleUnknownIntent(state, "未提供消息");
            }
            
            // 获取最后一条用户消息（去掉前缀）
            String lastMessage = messages.get(messages.size() - 1);
            String userContent = stripPrefix(lastMessage);
            
            // 使用大模型进行意图分类
            String prompt = String.format(INTENT_CLASSIFICATION_PROMPT, userContent);
            String response = chatModel.generate(prompt);
            String intentCode = response.trim().toLowerCase();
            
            // 映射为枚举
            AgentIntent intent = AgentIntent.fromCode(intentCode);
            
            Map<String, Object> updates = new HashMap<>();
            updates.put(HrAgentState.INTENT_KEY, intent.getCode());
            
            return updates;
            
        } catch (Exception e) {
            return handleUnknownIntent(state, "意图识别失败：" + e.getMessage());
        }
    }
    
    private Map<String, Object> handleUnknownIntent(HrAgentState state, String errorMessage) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.INTENT_KEY, AgentIntent.UNKNOWN.getCode());
        updates.put(HrAgentState.ERROR_MESSAGE_KEY, errorMessage);
        return updates;
    }
    
    private String stripPrefix(String msg) {
        if (msg.startsWith(HrAgentState.USER_PREFIX)) {
            return msg.substring(HrAgentState.USER_PREFIX.length());
        }
        if (msg.startsWith(HrAgentState.ASSISTANT_PREFIX)) {
            return msg.substring(HrAgentState.ASSISTANT_PREFIX.length());
        }
        return msg;
    }
}