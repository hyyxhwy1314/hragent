package org.example.hragent.agent.state;

/**
 * Agent 可识别的用户意图枚举
 */
public enum AgentIntent {
    
    // 员工相关意图
    QUERY_EMPLOYEE_INFO("query_employee_info", "查询员工信息"),
    QUERY_EMPLOYEE_LIST("query_employee_list", "查询员工列表"),
    
    // 岗位相关意图
    QUERY_JOB_INFO("query_job_info", "查询岗位信息"),
    QUERY_JOB_LIST("query_job_list", "查询岗位列表"),
    
    // 简历相关意图
    QUERY_RESUME_INFO("query_resume_info", "查询简历信息"),
    QUERY_RESUME_MATCH("query_resume_match", "查询简历匹配结果"),
    
    // 流程相关意图
    START_ONBOARDING_PROCESS("start_onboarding_process", "发起入职流程"),
    START_RESIGNATION_PROCESS("start_resignation_process", "发起离职流程"),
    START_TRANSFER_PROCESS("start_transfer_process", "发起调岗流程"),
    QUERY_PROCESS_STATUS("query_process_status", "查询流程状态"),
    
    // 绩效相关意图
    QUERY_PERFORMANCE_RESULT("query_performance_result", "查询绩效结果"),
    GENERATE_PERFORMANCE_REPORT("generate_performance_report", "生成绩效报告"),
    
    // 培训相关意图
    GENERATE_TRAINING_PLAN("generate_training_plan", "生成培训计划"),
    QUERY_TRAINING_COURSES("query_training_courses", "查询培训课程"),
    
    // 通用意图
    HR_KNOWLEDGE_QA("hr_knowledge_qa", "HR知识问答"),
    GENERAL_CONVERSATION("general_conversation", "一般对话"),
    UNKNOWN("unknown", "未知意图");
    
    private final String code;
    private final String description;
    
    AgentIntent(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static AgentIntent fromCode(String code) {
        for (AgentIntent intent : values()) {
            if (intent.code.equals(code)) {
                return intent;
            }
        }
        return UNKNOWN;
    }
}
