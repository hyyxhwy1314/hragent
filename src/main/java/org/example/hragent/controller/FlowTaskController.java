package org.example.hragent.controller;

import jakarta.validation.Valid;
import org.example.hragent.annotation.DistributedLock;
import org.example.hragent.annotation.LogAction;
import org.example.hragent.annotation.RepeatSubmit;
import org.example.hragent.dto.TaskCompleteDto;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.vo.R;
import org.example.hragent.vo.TaskVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 流程任务处理 Controller
 * <p>
 * 职责：
 * <ul>
 *   <li>查询当前用户的待办/已办任务</li>
 *   <li>完成（通过/拒绝）任务</li>
 *   <li>转办任务</li>
 * </ul>
 * 待办/已办不再手传 assigneeEmpId，从登录上下文取当前登录人。
 */
@RestController
@RequestMapping("/flow/tasks")
public class FlowTaskController {

    private final FlowOrchestratorService flowOrchestratorService;

    public FlowTaskController(FlowOrchestratorService flowOrchestratorService) {
        this.flowOrchestratorService = flowOrchestratorService;
    }

    /** 当前登录人待办任务 */
    @GetMapping("/todo")
    public R<List<TaskVO>> listTodo() {
        Long empId = CurrentUserService.requireEmpId();
        return R.ok(flowOrchestratorService.listTodoTasks(empId));
    }

    /** 当前登录人已办任务 */
    @GetMapping("/done")
    public R<List<TaskVO>> listDone() {
        Long empId = CurrentUserService.requireEmpId();
        return R.ok(flowOrchestratorService.listDoneTasks(empId));
    }

    /** 完成任务（通过/拒绝） */
    @PostMapping("/{taskId}/complete")
    @LogAction(module = "流程审批", action = "审批处理")
    @RepeatSubmit(interval = 3, unit = TimeUnit.SECONDS, message = "请勿重复提交审批")
    @DistributedLock(key = "'flow:task:' + #taskId", message = "该任务正在处理中，请稍后")
    public R<Boolean> complete(@PathVariable String taskId, @Valid @RequestBody TaskCompleteDto dto) {
        return R.ok(flowOrchestratorService.completeTask(taskId, dto));
    }

    /** 转办任务 */
    @PostMapping("/{taskId}/delegate")
    @LogAction(module = "流程审批", action = "转办任务")
    @RepeatSubmit(interval = 3, unit = TimeUnit.SECONDS, message = "请勿重复提交")
    @DistributedLock(key = "'flow:task:' + #taskId", message = "该任务正在处理中，请稍后")
    public R<Boolean> delegate(@PathVariable String taskId, @Valid @RequestBody TaskCompleteDto dto) {
        return R.ok(flowOrchestratorService.delegateTask(taskId, dto));
    }
}
