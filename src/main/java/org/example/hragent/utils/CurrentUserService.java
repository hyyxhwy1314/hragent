package org.example.hragent.utils;

import lombok.Data;

/**
 * 当前登录用户上下文（ThreadLocal）
 * <p>
 * 由 {@link org.example.hragent.interceptor.AuthInterceptor} 在请求进入时写入，
 * 业务代码通过 {@link #get()} 获取当前登录人，避免在接口参数中手传 empId。
 */
public class CurrentUserService {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前登录人是否为指定角色 */
    public static boolean hasRole(String role) {
        CurrentUser u = get();
        return u != null && role.equals(u.getRole());
    }

    /** 当前登录人员工ID，未登录返回 null */
    public static Long empId() {
        CurrentUser u = get();
        return u == null ? null : u.getEmpId();
    }

    @Data
    public static class CurrentUser {
        private Long empId;
        private String empName;
        private String role;

        public CurrentUser(Long empId, String empName, String role) {
            this.empId = empId;
            this.empName = empName;
            this.role = role;
        }
    }
}
