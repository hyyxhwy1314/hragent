package org.example.hragent.service.impl;

import org.example.hragent.entity.FlowInstance;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.utils.CurrentUserService;
import org.springframework.stereotype.Component;

/**
 * 流程访问权限校验：申请人本人或 HR/HRBP/ADMIN 方可查看或操作该流程实例
 */
@Component
public class FlowAccessGuard {

    /** 校验失败抛 NO_PERMISSION，未登录抛 UNAUTHORIZED */
    public void assertAccess(FlowInstance flowInstance, Long operatorEmpId) {
        BusinessException.throwIf(operatorEmpId == null, ErrorCode.UNAUTHORIZED);
        if (operatorEmpId.equals(flowInstance.getApplyEmpId())) {
            return;
        }
        CurrentUserService.CurrentUser user = CurrentUserService.get();
        String role = user == null ? null : user.getRole();
        BusinessException.throwIf(!"HR".equals(role) && !"HRBP".equals(role) && !"ADMIN".equals(role),
                ErrorCode.NO_PERMISSION, "无权查看或操作他人流程");
    }
}
