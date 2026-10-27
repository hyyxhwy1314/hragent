package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
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

    /** 业务主键ID（关联业务表：员工ID/简历ID/请假单ID）；不关联业务对象时可为空 */
    private Long bizId;

    /**
     * 业务对象类型：EMPLOYEE / RESUME / LEAVE_REQUEST；
     * 内置流程可省略（后端按 processKey 推断），自定义流程由发起时指定
     */
    private String bizType;

    /** 业务扩展数据 JSON 字符串 */
    private String bizJson;
}
