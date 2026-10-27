package org.example.hragent.flow.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.vo.FlowInstanceVO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 业务单发起流程的统一入口。
 * <p>
 * 业务单场景（请假、证明、入职等）都遵循「先落业务单 → 以业务单ID为 bizId 发起流程 → 回填流程实例ID」，
 * 且业务流程变量需平铺到 bizJson。手工拼接 JSON 字符串易出现转义错误，本类统一用 {@link ObjectMapper}
 * 序列化，并把序列化失败收敛为同一业务异常。
 */
@Slf4j
@Component
public class BizFlowStarter {

    private final FlowOrchestratorService flowOrchestratorService;
    private final ObjectMapper objectMapper;

    public BizFlowStarter(FlowOrchestratorService flowOrchestratorService, ObjectMapper objectMapper) {
        this.flowOrchestratorService = flowOrchestratorService;
        this.objectMapper = objectMapper;
    }

    /**
     * 发起业务流程。
     *
     * @param processKey 流程定义 key
     * @param bizId      业务单 ID
     * @param bizVars    业务流程变量（可为空）
     * @param bizLabel   业务名称，用于错误提示（如「请假」「证明」）
     * @return 流程实例视图（含流程单号与实例 ID）
     */
    public FlowInstanceVO start(String processKey, Long bizId, Map<String, Object> bizVars, String bizLabel) {
        FlowStartDto dto = new FlowStartDto();
        dto.setProcessKey(processKey);
        dto.setBizId(bizId);
        if (bizVars != null && !bizVars.isEmpty()) {
            try {
                dto.setBizJson(objectMapper.writeValueAsString(bizVars));
            } catch (Exception e) {
                log.error("{}业务变量序列化失败", bizLabel, e);
                throw new BusinessException(ErrorCode.OPERATION_FAILED, bizLabel + "业务参数序列化失败");
            }
        }
        return flowOrchestratorService.start(dto);
    }
}
