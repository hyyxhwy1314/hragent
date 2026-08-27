package org.example.hragent.agent.tools;

import org.example.hragent.agent.state.AgentIntent;
import org.example.hragent.agent.state.HrAgentState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工具编排服务
 * 负责多工具的串行和并行调用逻辑
 */
@Service
public class ToolOrchestrationService {

    private final HrBusinessTools hrBusinessTools;
    private final ExecutorService executorService;

    public ToolOrchestrationService(HrBusinessTools hrBusinessTools) {
        this.hrBusinessTools = hrBusinessTools;
        // 创建固定5线程的线程池，用于并行任务执行
        this.executorService = Executors.newFixedThreadPool(5);
    }

    /**
     * 根据意图执行工具，支持单工具、串行、并行三种执行方式
     * @param intent 用户意图枚举
     * @param query 用户原始提问文本
     * @param state Agent会话状态
     * @return 工具返回结果集合
     */
    public List<String> executeTools(AgentIntent intent, String query, HrAgentState state) {
        // 根据意图确定执行策略
        ExecutionStrategy strategy = getExecutionStrategy(intent);

        return switch (strategy) {
            case SINGLE -> executeSingleTool(intent, query, state);
            case SERIAL -> executeSerialTools(intent, query, state);
            case PARALLEL -> executeParallelTools(intent, query, state);
        };
    }

    /**
     * 根据意图匹配对应的工具执行策略
     */
    private ExecutionStrategy getExecutionStrategy(AgentIntent intent) {
        return switch (intent) {
            // 单工具执行意图
            case QUERY_EMPLOYEE_INFO, QUERY_JOB_INFO, QUERY_RESUME_INFO,
                 QUERY_PERFORMANCE_RESULT, QUERY_PROCESS_STATUS,
                 START_ONBOARDING_PROCESS, START_RESIGNATION_PROCESS,
                 START_TRANSFER_PROCESS, HR_KNOWLEDGE_QA -> ExecutionStrategy.SINGLE;

            // 串行执行意图（步骤顺序有依赖，必须先后执行）
            case GENERATE_TRAINING_PLAN, GENERATE_PERFORMANCE_REPORT -> ExecutionStrategy.SERIAL;

            // 并行执行意图（各个工具之间互不依赖，可同时运行）
            case QUERY_EMPLOYEE_LIST, QUERY_JOB_LIST, QUERY_TRAINING_COURSES -> ExecutionStrategy.PARALLEL;

            // 默认采用单工具执行
            default -> ExecutionStrategy.SINGLE;
        };
    }

    /**
     * 执行单个工具
     */
    private List<String> executeSingleTool(AgentIntent intent, String query, HrAgentState state) {
        String result = hrBusinessTools.executeToolByIntent(intent, query, state.userId());
        List<String> results = new ArrayList<>();
        results.add(result);
        return results;
    }

    /**
     * 按顺序串行执行多个有依赖关系的工具
     */
    private List<String> executeSerialTools(AgentIntent intent, String query, HrAgentState state) {
        List<String> results = new ArrayList<>();

        // 生成培训计划：先查员工信息，再查可用课程，最后生成计划
        if (intent == AgentIntent.GENERATE_TRAINING_PLAN) {
            // 步骤1：获取员工信息
            String employeeInfo = hrBusinessTools.queryEmployeeInfo(query, state.userId());
            results.add(employeeInfo);

            // 步骤2：获取可用培训课程
            String trainingCourses = hrBusinessTools.queryTrainingCourses("");
            results.add(trainingCourses);

            // 步骤3：生成培训计划
            String trainingPlan = hrBusinessTools.generateTrainingPlan(query, state.userId());
            results.add(trainingPlan);
        }

        // 生成绩效报告
        if (intent == AgentIntent.GENERATE_PERFORMANCE_REPORT) {
            // 步骤1：获取绩效结果
            String performanceResult = hrBusinessTools.queryPerformanceResult(query, state.userId());
            results.add(performanceResult);

            // 步骤2：生成报告
            String report = hrBusinessTools.generatePerformanceReport(query, state.userId());
            results.add(report);
        }

        return results;
    }

    /**
     * 并行执行多个互不依赖的工具任务
     */
    private List<String> executeParallelTools(AgentIntent intent, String query, HrAgentState state) {
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // 查询员工列表
        if (intent == AgentIntent.QUERY_EMPLOYEE_LIST) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    hrBusinessTools.queryEmployeeList(query, state.userId()), executorService));
        }

        // 查询岗位列表
        if (intent == AgentIntent.QUERY_JOB_LIST) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    hrBusinessTools.queryJobList(query), executorService));
        }

        // 查询培训课程
        if (intent == AgentIntent.QUERY_TRAINING_COURSES) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    hrBusinessTools.queryTrainingCourses(query), executorService));
        }

        // 等待所有异步任务全部执行完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.join();
            List<String> results = new ArrayList<>();
            for (CompletableFuture<String> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (Exception e) {
            List<String> errorResults = new ArrayList<>();
            errorResults.add("并行工具执行出错：" + e.getMessage());
            return errorResults;
        }
    }

    /**
     * 工具执行策略枚举
     */
    private enum ExecutionStrategy {
        SINGLE,    // 单工具执行
        SERIAL,    // 多工具串行执行
        PARALLEL   // 多工具并行执行
    }
}
