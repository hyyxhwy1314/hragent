package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowInstanceSaveDto {

    /**
     * 流程类型，如leave-apply、recruit-approve
     */
    @NotBlank(message = "流程类型不能为空")
    private String flowType;

    /**
     * 业务数据主键ID（关联业务表）
     */
    @NotNull(message = "业务id不能为空")
    private Long bizId;

    /**
     * 申请人员工ID
     */
    @NotNull(message = "申请人id不能为空")
    private Long applyEmpId;

    /**
     * Flowable流程实例ID
     */
    private String flowableProcInstId;

    /**
     * 业务扩展数据JSON字符串
     */
    private String bizJson;
}
