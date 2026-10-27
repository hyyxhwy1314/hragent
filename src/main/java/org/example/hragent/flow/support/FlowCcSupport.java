package org.example.hragent.flow.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.entity.FlowCc;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.FlowCcMapper;
import org.example.hragent.mapper.FlowInstanceMapper;
import org.example.hragent.service.impl.EmployeeNameResolver;
import org.example.hragent.vo.FlowCcVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程抄送支持：抄送记录写入、查询「抄送我的」列表、标记已读
 */
@Component
public class FlowCcSupport {

    private final FlowCcMapper flowCcMapper;
    private final FlowInstanceMapper flowInstanceMapper;
    private final EmployeeNameResolver employeeNameResolver;
    private final FlowTaskViewAssembler flowTaskViewAssembler;

    public FlowCcSupport(FlowCcMapper flowCcMapper, FlowInstanceMapper flowInstanceMapper,
                         EmployeeNameResolver employeeNameResolver,
                         FlowTaskViewAssembler flowTaskViewAssembler) {
        this.flowCcMapper = flowCcMapper;
        this.flowInstanceMapper = flowInstanceMapper;
        this.employeeNameResolver = employeeNameResolver;
        this.flowTaskViewAssembler = flowTaskViewAssembler;
    }

    /** 抄送我的流程列表 */
    public List<FlowCcVO> listMyCc(Long empId) {
        List<FlowCc> ccs = flowCcMapper.selectList(new LambdaQueryWrapper<FlowCc>()
                .eq(FlowCc::getCcEmpId, empId)
                .orderByDesc(FlowCc::getId));
        if (ccs.isEmpty()) {
            return new ArrayList<>();
        }
        // 批量取流程实例，避免逐条 selectById
        Set<Long> flowInstanceIds = ccs.stream()
                .map(FlowCc::getFlowInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, FlowInstance> instMap = flowInstanceIds.isEmpty() ? new HashMap<>()
                : flowInstanceMapper.selectBatchIds(flowInstanceIds).stream()
                    .collect(Collectors.toMap(FlowInstance::getId, f -> f, (a, b) -> a));
        // 批量回填申请人姓名与业务对象展示名，避免前端全量拉取员工/简历表做映射
        Map<Long, String> applyNameMap = employeeNameResolver.resolveNames(
                instMap.values().stream().map(FlowInstance::getApplyEmpId).collect(Collectors.toList()));
        Map<Long, String> bizNameMap = flowTaskViewAssembler.resolveInstanceBizNames(new ArrayList<>(instMap.values()));
        return ccs.stream().map(cc -> {
            FlowCcVO vo = new FlowCcVO();
            vo.setId(cc.getId());
            vo.setFlowInstanceId(cc.getFlowInstanceId());
            vo.setNodeName(cc.getNodeName());
            vo.setReadFlag(cc.getReadFlag() != null && cc.getReadFlag() == 1);
            vo.setCreateTime(cc.getCreateTime());
            FlowInstance fi = instMap.get(cc.getFlowInstanceId());
            if (fi != null) {
                vo.setFlowNo(fi.getFlowNo());
                vo.setFlowType(fi.getFlowType());
                vo.setBizId(fi.getBizId());
                vo.setApplyEmpId(fi.getApplyEmpId());
                vo.setApplyEmpName(applyNameMap.get(fi.getApplyEmpId()));
                vo.setBizName(bizNameMap.get(fi.getId()));
                vo.setFlowStatus(fi.getFlowStatus());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /** 标记抄送记录已读（仅本人可操作） */
    public boolean markCcRead(Long ccId, Long empId) {
        FlowCc cc = flowCcMapper.selectById(ccId);
        BusinessException.throwIf(cc == null, ErrorCode.DATA_NOT_FOUND, "抄送记录不存在");
        BusinessException.throwIf(!empId.equals(cc.getCcEmpId()), ErrorCode.NO_PERMISSION, "无权操作他人抄送记录");
        cc.setReadFlag(1);
        flowCcMapper.updateById(cc);
        return true;
    }

    /** 批量写入抄送记录（去重、跳过空值） */
    public void saveRecords(Long flowInstanceId, String nodeName, List<Long> ccEmpIds) {
        if (ccEmpIds == null || ccEmpIds.isEmpty()) {
            return;
        }
        ccEmpIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(empId -> {
                    FlowCc cc = new FlowCc();
                    cc.setFlowInstanceId(flowInstanceId);
                    cc.setNodeName(nodeName);
                    cc.setCcEmpId(empId);
                    cc.setReadFlag(0);
                    flowCcMapper.insert(cc);
                });
    }
}
