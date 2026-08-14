package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlowInstanceSaveDto {

    @NotBlank(message = "流程类型不能为空")
    private String flowType;

    @NotNull(message = "业务id不能为空")
    private Long bizId;

    @NotNull(message = "申请人id不能为空")
    private Long applyEmpId;

    private String flowableProcInstId;

    private String bizJson;
}