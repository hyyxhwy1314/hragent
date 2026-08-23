package org.example.hragent.constant;

/**
 * 流程类型与状态常量
 * <p>
 * 统一管理流程相关字符串/数字常量，避免散落各处硬编码。
 */
public final class FlowConstants {

    private FlowConstants() {}

    /** 流程定义 key —— 与 BPMN 中 process id 对齐 */
    public static final String PROC_KEY_ONBOARD = "onboard-process";
    public static final String PROC_KEY_REGULAR = "regular-process";
    public static final String PROC_KEY_TRANSFER = "transfer-process";
    public static final String PROC_KEY_LEAVE = "leave-process";

    /** 流程类型 —— 写入 t_flow_instance.flow_type */
    public static final String FLOW_TYPE_ONBOARD = "ONBOARD";
    public static final String FLOW_TYPE_REGULAR = "REGULAR";
    public static final String FLOW_TYPE_TRANSFER = "TRANSFER";
    public static final String FLOW_TYPE_LEAVE = "LEAVE";

    /** 流程状态 —— 写入 t_flow_instance.flow_status */
    public static final int STATUS_RUNNING = 1;     // 进行中
    public static final int STATUS_APPROVED = 2;     // 通过/完成
    public static final int STATUS_REJECTED = 3;      // 拒绝/驳回
    public static final int STATUS_CANCELED = 4;      // 撤回

    /** 流程 key → 类型名 映射 */
    public static String flowTypeOf(String processKey) {
        switch (processKey) {
            case PROC_KEY_ONBOARD: return FLOW_TYPE_ONBOARD;
            case PROC_KEY_REGULAR: return FLOW_TYPE_REGULAR;
            case PROC_KEY_TRANSFER: return FLOW_TYPE_TRANSFER;
            case PROC_KEY_LEAVE: return FLOW_TYPE_LEAVE;
            default: return processKey.toUpperCase();
        }
    }
}
