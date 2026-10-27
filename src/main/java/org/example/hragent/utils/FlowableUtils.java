package org.example.hragent.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Flowable 相关轻量工具
 * <p>
 * 收敛散落各处的「处理人ID解析」「Date → LocalDateTime」等小工具逻辑，避免跨类重复实现。
 */
public final class FlowableUtils {

    private FlowableUtils() {}

    /** Flowable assignee（员工ID字符串）→ Long，null/空白/非数字返回 null */
    public static Long parseEmpId(String assignee) {
        if (assignee == null || assignee.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(assignee);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Flowable Date → LocalDateTime 转换（系统时区） */
    public static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
