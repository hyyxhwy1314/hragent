package org.example.hragent.feishu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批飞书卡片构造器。
 * <p>
 * 生成一张含「同意 / 拒绝」按钮的交互卡片；按钮 value 内嵌 taskId、审批人员工ID、审批人 open_id、
 * 流程实例ID与审批结果，用户点击后由飞书回调（card.action.trigger）回传，驱动既有 Flowable 审批。
 * 审批人 open_id 用于回调时校验操作人身份，防止卡片被转发后由他人代为审批。
 * <p>
 * 审批处理后回调返回 {@link #buildResolved} 生成的「结果卡片」（不含按钮），替换原卡片以禁止重复点击。
 */
public final class ApprovalCardBuilder {

    private ApprovalCardBuilder() {}

    /**
     * 构建待办审批卡片（含同意/拒绝按钮）
     *
     * @param taskId          Flowable 任务ID
     * @param approverEmpId   审批人员工ID（内嵌到按钮，回调时用于校验归属）
     * @param approverOpenId  审批人飞书 open_id（内嵌到按钮，回调时用于校验操作人）
     * @param procInstId      Flowable 流程实例ID（回调时用于重建卡片正文）
     * @param taskName        节点名称（回调时用于重建卡片正文）
     * @param title           卡片标题（如「请假审批待办」）
     * @param content         Markdown 正文
     * @param objectMapper    用于序列化卡片（避免手工拼串转义问题）
     */
    public static String build(String taskId, Long approverEmpId, String approverOpenId, String procInstId,
                               String taskName, String title, String content, ObjectMapper objectMapper) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));
        card.put("header", Map.of(
                "template", "blue",
                "title", Map.of("tag", "plain_text", "content", title)));

        card.put("elements", List.of(
                Map.of("tag", "div", "text", Map.of("tag", "lark_md", "content", content)),
                Map.of("tag", "hr"),
                Map.of("tag", "action", "actions", List.of(
                        button("同意", "primary", taskId, approverEmpId, approverOpenId, procInstId, taskName, true),
                        button("拒绝", "danger", taskId, approverEmpId, approverOpenId, procInstId, taskName, false))),
                Map.of("tag", "note", "elements", List.of(
                        Map.of("tag", "plain_text", "content", "该操作仅限审批人本人，处理后不可撤销。")))));

        return write(card, objectMapper);
    }

    /**
     * 构建「已处理」结果卡片：保留原正文，去掉操作按钮，仅保留结果提示。
     * <p>
     * 用于卡片回调的成功响应，替换原卡片后按钮消失，用户无法重复点击。
     *
     * @param template     头部配色（green=已同意 / red=已拒绝 / grey=已处理过）
     * @param title        头部标题
     * @param content      Markdown 正文（沿用原卡片内容）
     * @param note         底部提示文案
     */
    public static String buildResolved(String template, String title, String content,
                                       String note, ObjectMapper objectMapper) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));
        card.put("header", Map.of(
                "template", template,
                "title", Map.of("tag", "plain_text", "content", title)));

        card.put("elements", List.of(
                Map.of("tag", "div", "text", Map.of("tag", "lark_md", "content", content)),
                Map.of("tag", "hr"),
                Map.of("tag", "note", "elements", List.of(
                        Map.of("tag", "plain_text", "content", note)))));

        return write(card, objectMapper);
    }

    private static Map<String, Object> button(String label, String type, String taskId,
                                              Long approverEmpId, String approverOpenId,
                                              String procInstId, String taskName, boolean approved) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("taskId", taskId);
        value.put("approverEmpId", approverEmpId);
        value.put("approverOpenId", approverOpenId);
        value.put("procInstId", procInstId);
        value.put("taskName", taskName);
        value.put("approved", approved);
        return Map.of(
                "tag", "button",
                "text", Map.of("tag", "plain_text", "content", label),
                "type", type,
                "value", value);
    }

    private static String write(Map<String, Object> card, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(card);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审批卡片序列化失败", e);
        }
    }
}
