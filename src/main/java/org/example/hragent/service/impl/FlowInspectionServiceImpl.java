package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.service.FlowInspectionService;
import org.example.hragent.vo.StuckFlowVO;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程异常巡检服务实现。
 * <p>
 * 实现要点：
 * <ul>
 *   <li>游标分页：按 id 递增、每批 {@link #PAGE_SIZE} 条，逐批处理，避免全表扫描与长事务</li>
 *   <li>超时判定：取该流程实例当前所有活动任务中最早的任务创建时间，计算已停留小时数</li>
 *   <li>处理人/申请人姓名批量回填，避免 N+1 查询</li>
 * </ul>
 */
@Slf4j
@Service
public class FlowInspectionServiceImpl implements FlowInspectionService {

    /** 游标分页批大小 */
    private static final int PAGE_SIZE = 200;

    private final FlowInstanceMapper flowInstanceMapper;
    private final EmployeeMapper employeeMapper;
    private final org.flowable.engine.TaskService taskService;

    public FlowInspectionServiceImpl(FlowInstanceMapper flowInstanceMapper,
                                     EmployeeMapper employeeMapper,
                                     org.flowable.engine.TaskService taskService) {
        this.flowInstanceMapper = flowInstanceMapper;
        this.employeeMapper = employeeMapper;
        this.taskService = taskService;
    }

    @Override
    public List<StuckFlowVO> scanStuckFlows(int thresholdHours) {
        long lastId = 0L;
        List<StuckFlowVO> stuck = new ArrayList<>();
        while (true) {
            List<FlowInstance> page = flowInstanceMapper.selectList(new LambdaQueryWrapper<FlowInstance>()
                    .eq(FlowInstance::getFlowStatus, FlowConstants.STATUS_RUNNING)
                    .gt(FlowInstance::getId, lastId)
                    .orderByAsc(FlowInstance::getId)
                    .last("LIMIT " + PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }
            for (FlowInstance instance : page) {
                StuckFlowVO vo = toStuck(instance, thresholdHours);
                if (vo != null) {
                    stuck.add(vo);
                }
            }
            lastId = page.get(page.size() - 1).getId();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        // 姓名批量回填
        fillNames(stuck);
        stuck.sort(Comparator.comparing(StuckFlowVO::getPendingHours).reversed());
        return stuck;
    }

    /** 单实例超时判定：无活动任务或停留不足阈值返回 null */
    private StuckFlowVO toStuck(FlowInstance instance, int thresholdHours) {
        String procInstId = instance.getFlowableProcInstId();
        if (procInstId == null || procInstId.isBlank()) {
            return null;
        }
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(procInstId).list();
        if (tasks.isEmpty()) {
            return null;
        }
        // 最早的活动任务 → 停留起点；当前停留的节点名称合并展示
        Task earliest = tasks.stream()
                .filter(t -> t.getCreateTime() != null)
                .min(Comparator.comparing(Task::getCreateTime))
                .orElse(null);
        if (earliest == null) {
            return null;
        }
        long pendingHours = Duration.between(earliest.getCreateTime().toInstant(), Instant.now()).toHours();
        if (pendingHours < thresholdHours) {
            return null;
        }
        StuckFlowVO vo = new StuckFlowVO();
        vo.setFlowInstanceId(instance.getId());
        vo.setFlowNo(instance.getFlowNo());
        vo.setFlowType(instance.getFlowType());
        vo.setBizId(instance.getBizId());
        vo.setApplyEmpId(instance.getApplyEmpId());
        vo.setPendingHours(pendingHours);
        vo.setCurrentNode(tasks.stream()
                .map(Task::getName)
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .collect(Collectors.joining("、")));
        vo.setAssigneeEmpId(parseEmpId(earliest.getAssignee()));
        return vo;
    }

    /** 批量回填申请人、处理人姓名（去重后一次查询） */
    private void fillNames(List<StuckFlowVO> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> empIds = list.stream()
                .flatMap(v -> java.util.stream.Stream.of(v.getApplyEmpId(), v.getAssigneeEmpId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (empIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = employeeMapper.selectBatchIds(empIds).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getEmpName, (a, b) -> a));
        list.forEach(v -> {
            v.setApplyEmpName(nameMap.getOrDefault(v.getApplyEmpId(), ""));
            v.setAssigneeName(nameMap.getOrDefault(v.getAssigneeEmpId(), ""));
        });
    }

    /** Flowable 处理人（员工ID字符串）→ Long，解析失败返回 null */
    private Long parseEmpId(String assignee) {
        if (assignee == null || assignee.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(assignee);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}