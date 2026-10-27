package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.constant.FlowConstants;
import org.example.hragent.converter.FlowInstanceConverter;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.entity.Resume;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.mapper.ResumeMapper;
import org.example.hragent.utils.FlowableUtils;
import org.example.hragent.vo.FlowInstanceVO;
import org.example.hragent.vo.TaskVO;
import org.flowable.task.api.TaskInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 流程任务视图装配
 * <p>
 * 把待办/已办任务批量转换为 {@link TaskVO}：先一次性取全部流程实例、员工姓名、简历名，避免逐条查询（N+1）。
 * Task（待办）与 HistoricTaskInstance（已办）均实现 TaskInfo，共用本组件。
 */
@Component
public class FlowTaskViewAssembler {

    private final FlowInstanceMapper flowInstanceMapper;
    private final ResumeMapper resumeMapper;
    private final EmployeeNameResolver employeeNameResolver;
    private final FlowInstanceConverter flowInstanceConverter;

    public FlowTaskViewAssembler(FlowInstanceMapper flowInstanceMapper,
                                 ResumeMapper resumeMapper,
                                 EmployeeNameResolver employeeNameResolver,
                                 FlowInstanceConverter flowInstanceConverter) {
        this.flowInstanceMapper = flowInstanceMapper;
        this.resumeMapper = resumeMapper;
        this.employeeNameResolver = employeeNameResolver;
        this.flowInstanceConverter = flowInstanceConverter;
    }

    /**
     * 把流程实例批量转换为 {@link FlowInstanceVO}：一次性解析申请人姓名与业务对象展示名，避免 N+1。
     *
     * @param instances 流程实例列表
     * @return 已回填姓名/业务名的视图对象列表
     */
    public List<FlowInstanceVO> toInstanceVOs(List<FlowInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> empNameMap = employeeNameResolver.resolveNames(
                instances.stream().map(FlowInstance::getApplyEmpId).collect(Collectors.toList()));
        Map<Long, String> bizNameMap = resolveInstanceBizNames(instances);

        return instances.stream().map(fi -> {
            FlowInstanceVO vo = flowInstanceConverter.entityToVo(fi);
            vo.setApplyEmpName(empNameMap.get(fi.getApplyEmpId()));
            vo.setBizName(bizNameMap.get(fi.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    public List<TaskVO> toTaskVOs(List<? extends TaskInfo> tasks, Long currentEmpId) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }
        // 批量取流程实例
        List<String> procInstIds = tasks.stream()
                .map(TaskInfo::getProcessInstanceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<String, FlowInstance> instMap = procInstIds.isEmpty() ? new HashMap<>()
                : flowInstanceMapper.selectList(new LambdaQueryWrapper<FlowInstance>()
                        .in(FlowInstance::getFlowableProcInstId, procInstIds)).stream()
                    .collect(Collectors.toMap(FlowInstance::getFlowableProcInstId, f -> f, (a, b) -> a));

        // 汇总需要解析姓名的员工ID 与 需要解析名称的简历ID（入职流程 bizId 为简历ID）
        List<Long> empIds = new ArrayList<>();
        List<Long> resumeIds = new ArrayList<>();
        tasks.forEach(t -> {
            empIds.add(FlowableUtils.parseEmpId(t.getAssignee()));
            FlowInstance fi = instMap.get(t.getProcessInstanceId());
            if (fi != null && fi.getBizId() != null) {
                if (FlowConstants.BIZ_TYPE_RESUME.equals(fi.resolvedBizType())) {
                    resumeIds.add(fi.getBizId());
                } else if (FlowConstants.BIZ_TYPE_EMPLOYEE.equals(fi.resolvedBizType())) {
                    empIds.add(fi.getBizId());
                }
                // 请假单等其他业务对象非员工/简历，无需解析名称
            }
            if (fi != null) {
                empIds.add(fi.getApplyEmpId());
            }
        });
        Map<Long, String> empNameMap = employeeNameResolver.resolveNames(empIds);
        Map<Long, String> resumeNameMap = loadResumeNames(resumeIds);

        return tasks.stream().map(t -> {
            TaskVO vo = new TaskVO();
            vo.setTaskId(t.getId());
            vo.setProcessInstanceId(t.getProcessInstanceId());
            vo.setTaskName(t.getName());
            vo.setAssigneeEmpId(currentEmpId);
            vo.setAssigneeName(empNameMap.get(FlowableUtils.parseEmpId(t.getAssignee())));
            vo.setCreateTime(FlowableUtils.toLocalDateTime(t.getCreateTime()));
            FlowInstance fi = instMap.get(t.getProcessInstanceId());
            if (fi != null) {
                vo.setFlowInstanceId(fi.getId());
                vo.setFlowType(fi.getFlowType());
                vo.setBizId(fi.getBizId());
                vo.setApplyEmpId(fi.getApplyEmpId());
                vo.setApplyEmpName(empNameMap.get(fi.getApplyEmpId()));
                vo.setBizName(resolveBizName(fi, empNameMap, resumeNameMap));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /** 批量解析简历名称：ID → resumeName */
    private Map<Long, String> loadResumeNames(Collection<Long> ids) {
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return new HashMap<>();
        }
        return resumeMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(Resume::getId, Resume::getResumeName, (a, b) -> a));
    }

    /**
     * 批量解析流程实例的业务对象展示名（key = flowInstanceId）。
     * 供流程实例列表、抄送列表等场景复用，避免各自重复实现 bizType 推断与名称映射。
     */
    public Map<Long, String> resolveInstanceBizNames(Collection<FlowInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> empIds = new ArrayList<>();
        List<Long> resumeIds = new ArrayList<>();
        for (FlowInstance fi : instances) {
            if (fi.getBizId() == null) {
                continue;
            }
            String bizType = fi.resolvedBizType();
            if (FlowConstants.BIZ_TYPE_RESUME.equals(bizType)) {
                resumeIds.add(fi.getBizId());
            } else if (FlowConstants.BIZ_TYPE_EMPLOYEE.equals(bizType)) {
                empIds.add(fi.getBizId());
            }
            // 请假单/证明等业务对象非员工/简历，无需解析名称
        }
        Map<Long, String> empNameMap = employeeNameResolver.resolveNames(empIds);
        Map<Long, String> resumeNameMap = loadResumeNames(resumeIds);
        return instances.stream()
                .filter(fi -> fi.getId() != null)
                .collect(Collectors.toMap(FlowInstance::getId,
                        fi -> resolveBizName(fi, empNameMap, resumeNameMap), (a, b) -> a));
    }

    /** 业务对象展示名：简历→简历名，员工→姓名，请假单→"请假单#id"，无业务对象→null */
    private String resolveBizName(FlowInstance fi, Map<Long, String> empNames, Map<Long, String> resumeNames) {
        if (fi.getBizId() == null) {
            return null;
        }
        return switch (fi.resolvedBizType()) {
            case FlowConstants.BIZ_TYPE_RESUME ->
                    resumeNames.getOrDefault(fi.getBizId(), "简历#" + fi.getBizId());
            case FlowConstants.BIZ_TYPE_LEAVE_REQUEST -> "请假单#" + fi.getBizId();
            case FlowConstants.BIZ_TYPE_CERTIFICATE -> "证明#" + fi.getBizId();
            default -> empNames.getOrDefault(fi.getBizId(), "#" + fi.getBizId());
        };
    }

}
