package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.flow.support.FlowTaskViewAssembler;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.vo.FlowCcVO;
import org.example.hragent.vo.FlowDiagramVO;
import org.example.hragent.vo.FlowInstanceVO;
import org.example.hragent.vo.FlowTraceVO;
import org.example.hragent.vo.PageVO;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程查询 Controller
 * <p>
 * 职责：
 * <ul>
 *   <li>查询流程实例列表（按类型/状态筛选，服务端分页）</li>
 *   <li>查询流程审批轨迹</li>
 *   <li>查询流程运行态图（BPMN 高亮）</li>
 *   <li>查询/标记抄送我的流程</li>
 * </ul>
 */
@RestController
@RequestMapping("/flow/query")
public class FlowQueryController {

    private final FlowOrchestratorService flowOrchestratorService;
    private final FlowInstanceMapper flowInstanceMapper;
    private final FlowTaskViewAssembler flowTaskViewAssembler;

    public FlowQueryController(FlowOrchestratorService flowOrchestratorService,
                               FlowInstanceMapper flowInstanceMapper,
                               FlowTaskViewAssembler flowTaskViewAssembler) {
        this.flowOrchestratorService = flowOrchestratorService;
        this.flowInstanceMapper = flowInstanceMapper;
        this.flowTaskViewAssembler = flowTaskViewAssembler;
    }

    /**
     * 流程实例列表（服务端分页）
     *
     * @param flowType 流程类型（可选）
     * @param flowStatus 状态（可选）
     * @param applyEmpId 申请人ID（可选）
     * @param pageNum 页码，从 1 开始（可选，默认 1）
     * @param pageSize 每页条数（可选，默认 10）
     */
    @GetMapping("/instances")
    public R<PageVO<FlowInstanceVO>> listInstances(@RequestParam(required = false) String flowType,
                                                    @RequestParam(required = false) Integer flowStatus,
                                                    @RequestParam(required = false) Long applyEmpId,
                                                    @RequestParam(required = false, defaultValue = "1") Long pageNum,
                                                    @RequestParam(required = false, defaultValue = "10") Long pageSize) {
        LambdaQueryWrapper<FlowInstance> wrapper = new LambdaQueryWrapper<FlowInstance>()
                .eq(flowType != null && !flowType.isBlank(), FlowInstance::getFlowType, flowType)
                .eq(flowStatus != null, FlowInstance::getFlowStatus, flowStatus)
                .eq(applyEmpId != null, FlowInstance::getApplyEmpId, applyEmpId)
                .orderByDesc(FlowInstance::getId);
        Page<FlowInstance> page = flowInstanceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(PageVO.of(flowTaskViewAssembler.toInstanceVOs(page.getRecords()),
                page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 流程审批轨迹 */
    @GetMapping("/instances/{flowInstanceId}/trace")
    public R<List<FlowTraceVO>> getTrace(@PathVariable Long flowInstanceId) {
        return R.ok(flowOrchestratorService.getTrace(flowInstanceId));
    }

    /** 流程运行态图：BPMN XML + 活动/已完成节点高亮 */
    @GetMapping("/instances/{flowInstanceId}/diagram")
    public R<FlowDiagramVO> getDiagram(@PathVariable Long flowInstanceId) {
        return R.ok(flowOrchestratorService.getDiagram(flowInstanceId));
    }

    /** 抄送我的流程列表 */
    @GetMapping("/cc/my")
    public R<List<FlowCcVO>> listMyCc() {
        Long empId = CurrentUserService.requireEmpId();
        return R.ok(flowOrchestratorService.listMyCc(empId));
    }

    /** 标记抄送记录已读 */
    @PostMapping("/cc/{ccId}/read")
    public R<Boolean> markCcRead(@PathVariable Long ccId) {
        Long empId = CurrentUserService.requireEmpId();
        return R.ok(flowOrchestratorService.markCcRead(ccId, empId));
    }
}