package org.example.hragent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务完成请求
 * <p>
 * approverEmpId 不再手传，由 {@link org.example.hragent.utils.CurrentUserService} 从登录上下文获取。
 */
@Data
public class TaskCompleteDto {

    /** 是否通过；true=通过，false=拒绝 */
    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    /** 审批意见 */
    private String comment;

    /** 转办目标员工ID（仅转办时填写） */
    private Long delegateToEmpId;
}
