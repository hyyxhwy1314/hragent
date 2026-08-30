package org.example.hragent.agent.tools;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * HR Agent AI 服务接口
 * <p>
 * 通过 LangChain4j AiServices 自动绑定 HrBusinessTools 中的 @Tool 方法，
 * 大模型根据用户消息自动选择并调用合适的工具，无需手动意图识别。
 * <p>
 * 支持 per-session ChatMemory：通过 memoryId 参数区分不同会话的对话上下文，
 * 由 HrAgentAiServiceFactory 为每个会话创建独立实例。
 */
public interface HrAgentAiService {

    @SystemMessage("""
            你是一个专业的 HR 人力资源智能助手。你可以帮助用户：
            - 查询员工、岗位、简历、绩效等信息
            - 发起入职、离职、调岗等审批流程
            - 查询培训课程和生成培训计划
            - 回答人力资源政策相关问题
            
            请根据用户的问题，调用合适的工具来获取信息并给出准确的回答。
            如果用户的问题不需要调用工具（如普通闲聊），直接回复即可。
            回答请使用中文。
            """)
    String chat(@MemoryId Long memoryId, @UserMessage String message);
}
