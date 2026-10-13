package org.example.hragent.agent.task;

import org.example.hragent.agent.controller.AgentController.ChatResponse;
import org.example.hragent.agent.graph.AgentScheduler;
import org.example.hragent.agent.state.HrAgentState;
import org.example.hragent.agent.state.ToolCallRecord;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.utils.CurrentUserService.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Agent 异步任务调度服务
 * <p>
 * 将耗时的 Agent 工作流（LLM 推理 + 多轮工具调用）从 HTTP 请求线程剥离：
 * 提交任务立即返回 taskId，工作流在独立线程池中执行，每完成一个节点即更新
 * 任务进度，前端轮询任务状态即可看到实时进展。任务支持超时标记与失败记录，
 * 与 {@link AgentScheduler#executeTask} 配合使用。
 */
@Service
public class AgentAsyncTaskService {

    /** 任务状态 */
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 单任务进度步骤上限（防止多轮工具循环无限累积） */
    private static final int MAX_STEPS = 30;
    /** 已完成任务在内存中的保留时长（毫秒） */
    private static final long RETAIN_MS = 3600_000L;

    private final AgentScheduler agentScheduler;
    private final int maxDurationMs;
    private final ExecutorService executor;
    private final Map<String, AgentTaskRecord> tasks = new ConcurrentHashMap<>();

    public AgentAsyncTaskService(AgentScheduler agentScheduler,
                                 @Value("${hragent.agent.task.max-duration-ms:300000}") int maxDurationMs,
                                 @Value("${hragent.agent.task.thread-pool-size:4}") int poolSize) {
        this.agentScheduler = agentScheduler;
        this.maxDurationMs = maxDurationMs;
        this.executor = Executors.newFixedThreadPool(poolSize, daemonThreadFactory());
    }

    /**
     * 提交异步任务：立即登记任务并返回，工作流在后台线程池执行
     *
     * @param userMessage 用户输入的消息
     * @param userId      用户 ID（可选）
     * @param sessionId   会话 ID（为空时自动生成）
     * @param currentUser 当前登录用户快照（异步线程无请求上下文，
     *                    执行前重建 ThreadLocal 供工具内部获取登录态）
     * @return 任务记录（taskId 用于轮询查询）
     */
    public AgentTaskRecord submit(String userMessage, String userId, String sessionId, CurrentUser currentUser) {
        String effectiveSessionId = (sessionId == null || sessionId.isEmpty())
                ? UUID.randomUUID().toString()
                : sessionId;
        cleanupExpired();

        AgentTaskRecord record = new AgentTaskRecord(effectiveSessionId);
        tasks.put(record.getTaskId(), record);

        // 工作线程：重建登录上下文后执行工作流并按节点追加进度步骤，异常则标记 FAILED
        Future<?> work = executor.submit(() -> {
            CurrentUserService.set(currentUser);
            try {
                ChatResponse result = agentScheduler.executeTask(userMessage, userId, effectiveSessionId,
                        (node, state) -> appendStep(record, node, state));
                record.complete(result);
            } catch (Exception e) {
                record.markFailed(e.getMessage());
            } finally {
                CurrentUserService.clear();
            }
        });

        // 监督线程：超过最大时长标记 TIMEOUT 并中断工作线程
        executor.submit(() -> {
            try {
                work.get(maxDurationMs, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                work.cancel(true);
                record.markTimeout("任务执行超时（超过 " + maxDurationMs / 1000 + " 秒）");
            } catch (Exception ignored) {
                // 工作线程已通过 complete/markFailed 写入终态，此处无需处理
            }
        });

        return record;
    }

    /**
     * 查询任务记录（不存在时返回 null）
     */
    public AgentTaskRecord get(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 把节点级进度翻译为人类可读的步骤描述并追加到任务记录
     */
    private void appendStep(AgentTaskRecord record, String node, HrAgentState state) {
        String now = LocalDateTime.now().format(DTF);
        if ("model".equals(node)) {
            List<ToolCallRecord> calls = state.toolCalls();
            if (calls != null && !calls.isEmpty()) {
                String names = calls.stream()
                        .map(ToolCallRecord::name)
                        .filter(name -> name != null)
                        .collect(Collectors.joining("、"));
                record.addStep("[" + now + "] 正在调用工具：" + names);
            } else {
                record.addStep("[" + now + "] 正在思考…");
            }
        } else {
            record.addStep("[" + now + "] 正在执行工具…");
        }
    }

    /**
     * 提交任务时顺带清理已超过保留期的终态任务，防止内存膨胀
     */
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        tasks.entrySet().removeIf(entry -> {
            AgentTaskRecord record = entry.getValue();
            return record.isFinished() && now - record.getFinishedAt() > RETAIN_MS;
        });
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "agent-task-" + seq.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 异步任务记录：状态机保证仅 RUNNING 可转入终态，
     * 超时标记与正常完成并发发生时以先到者为准。
     */
    public static class AgentTaskRecord {
        private final String taskId;
        private final String sessionId;
        private volatile String status = STATUS_RUNNING;
        private final List<String> steps = new ArrayList<>();
        private ChatResponse result;
        private String error;
        private final long createdAt = System.currentTimeMillis();
        private volatile long finishedAt;

        public AgentTaskRecord(String sessionId) {
            this.taskId = UUID.randomUUID().toString();
            this.sessionId = sessionId;
        }

        public synchronized boolean complete(ChatResponse result) {
            return transitTo(STATUS_SUCCEEDED, result, null);
        }

        public synchronized boolean markFailed(String error) {
            return transitTo(STATUS_FAILED, null, error);
        }

        public synchronized boolean markTimeout(String error) {
            return transitTo(STATUS_TIMEOUT, null, error);
        }

        private boolean transitTo(String target, ChatResponse result, String error) {
            if (!STATUS_RUNNING.equals(status)) {
                return false;
            }
            this.status = target;
            this.result = result;
            this.error = error;
            this.finishedAt = System.currentTimeMillis();
            return true;
        }

        public synchronized void addStep(String step) {
            if (steps.size() < MAX_STEPS) {
                steps.add(step);
            }
        }

        public boolean isFinished() {
            return !STATUS_RUNNING.equals(status);
        }

        public String getTaskId() { return taskId; }
        public String getSessionId() { return sessionId; }
        public String getStatus() { return status; }
        public List<String> getSteps() { return steps; }
        public ChatResponse getResult() { return result; }
        public String getError() { return error; }
        public long getCreatedAt() { return createdAt; }
        public long getFinishedAt() { return finishedAt; }
    }
}
