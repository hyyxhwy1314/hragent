package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 流程发起请求
 * <p>
 * applyEmpId 不再手传，由 {@link org.example.hragent.utils.CurrentUserService} 从登录上下文获取。
 */
@Data
public class FlowStartDto {

    /** 流程定义 key，如 onboard-process */
    @NotBlank(message = "流程定义 key 不能为空")
    private String processKey;

    /** 业务主键ID（关联业务表，如员工ID） */
    @NotNull(message = "业务 id 不能为空")
    private Long bizId;

    /** 业务扩展数据 JSON 字符串 */
    private String bizJson;
}
