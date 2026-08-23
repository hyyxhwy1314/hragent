package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.vo.FlowTraceVO;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程查询 Controller
 * <p>
 * 职责：
 * <ul>
 *   <li>查询流程实例列表（按类型/状态筛选）</li>
 *   <li>查询流程审批轨迹</li>
 * </ul>
 */
@RestController
@RequestMapping("/flow/query")
public class FlowQueryController {

    private final FlowOrchestratorService flowOrchestratorService;
    private final FlowInstanceMapper flowInstanceMapper;

    public FlowQueryController(FlowOrchestratorService flowOrchestratorService,
                               FlowInstanceMapper flowInstanceMapper) {
        this.flowOrchestratorService = flowOrchestratorService;
        this.flowInstanceMapper = flowInstanceMapper;
    }

    /**
     * 流程实例列表
     *
     * @param flowType 流程类型（可选）
     * @param flowStatus 状态（可选）
     * @param applyEmpId 申请人ID（可选）
     */
    @GetMapping("/instances")
    public R<List<FlowInstance>> listInstances(@RequestParam(required = false) String flowType,
                                                @RequestParam(required = false) Integer flowStatus,
                                                @RequestParam(required = false) Long applyEmpId) {
        LambdaQueryWrapper<FlowInstance> wrapper = new LambdaQueryWrapper<FlowInstance>()
                .eq(flowType != null && !flowType.isBlank(), FlowInstance::getFlowType, flowType)
                .eq(flowStatus != null, FlowInstance::getFlowStatus, flowStatus)
                .eq(applyEmpId != null, FlowInstance::getApplyEmpId, applyEmpId)
                .orderByDesc(FlowInstance::getId);
        return R.ok(flowInstanceMapper.selectList(wrapper));
    }

    /** 流程审批轨迹 */
    @GetMapping("/instances/{flowInstanceId}/trace")
    public R<List<FlowTraceVO>> getTrace(@PathVariable Long flowInstanceId) {
        return R.ok(flowOrchestratorService.getTrace(flowInstanceId));
    }
}
