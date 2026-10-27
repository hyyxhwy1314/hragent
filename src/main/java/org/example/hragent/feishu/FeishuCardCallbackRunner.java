package org.example.hragent.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.CallBackCard;
import com.lark.oapi.event.cardcallback.model.CallBackToast;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.config.FeishuOpenApiProperties;
import org.example.hragent.dto.TaskCompleteDto;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.service.FlowOrchestratorService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 飞书卡片回调接收器（长连接模式）。
 * <p>
 * 通过官方 SDK 建立 WebSocket 长连接，订阅「卡片回传交互 card.action.trigger」：
 * 用户在审批卡片上点击「同意 / 拒绝」后，回调携带按钮内嵌的 taskId / 审批人员工ID / 审批人 open_id /
 * 节点名 / 审批结果，由本类转为 {@link TaskCompleteDto} 并调用
 * {@link FlowOrchestratorService#completeTaskAs} 驱动既有审批流。
 * <p>
 * 与真实企业审批机器人一致的三道校验：
 * <ol>
 *   <li>操作人身份校验：回调中的操作人 open_id 必须与按钮绑定的审批人 open_id 一致，
 *       防止卡片被转发后由他人代为审批</li>
 *   <li>重复处理拦截：任务已不处于待办状态时直接返回「已处理」卡片，不重复流转</li>
 *   <li>任务归属校验：由 {@code completeTaskAs} 再次校验 assignee，作为最终兜底</li>
 * </ol>
 * 回调响应返回更新后的卡片（去掉按钮、显示处理结果）替换原卡片，从而禁止同一审批被重复点击。
 * <p>
 * 长连接 {@code client.start()} 为阻塞调用，故在独立守护线程中启动；未配置凭据时整体不启动。
 */
@Slf4j
@Component
public class FeishuCardCallbackRunner implements ApplicationRunner {

    private static final String ACTION_TASK_ID = "taskId";
    private static final String ACTION_APPROVER_EMP_ID = "approverEmpId";
    private static final String ACTION_APPROVER_OPEN_ID = "approverOpenId";
    private static final String ACTION_PROC_INST_ID = "procInstId";
    private static final String ACTION_TASK_NAME = "taskName";
    private static final String ACTION_APPROVED = "approved";

    /** 飞书 Toast 文案上限（超出会导致回调响应被拒） */
    private static final int TOAST_MAX_LENGTH = 100;

    private final FeishuOpenApiProperties props;
    private final ObjectMapper objectMapper;
    private final FlowOrchestratorService flowOrchestratorService;
    private final FeishuApprovalPushService pushService;

    public FeishuCardCallbackRunner(FeishuOpenApiProperties props,
                                    ObjectMapper objectMapper,
                                    FlowOrchestratorService flowOrchestratorService,
                                    FeishuApprovalPushService pushService) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.flowOrchestratorService = flowOrchestratorService;
        this.pushService = pushService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isEnabled() || !props.hasCredentials()) {
            log.info("飞书审批卡片回调未启用或未配置凭据，跳过长连接");
            return;
        }
        EventDispatcher dispatcher = EventDispatcher.newBuilder("", "") // 长连接无需验签参数，保持空字符串
                .onP2CardActionTrigger(new P2CardActionTriggerHandler() {
                    @Override
                    public P2CardActionTriggerResponse handle(P2CardActionTrigger event) {
                        return onCardActionTrigger(event);
                    }
                })
                .build();

        Thread thread = new Thread(() -> {
            try {
                new com.lark.oapi.ws.Client.Builder(props.getAppId(), props.getAppSecret())
                        .eventHandler(dispatcher)
                        .build()
                        .start();
            } catch (Exception e) {
                log.error("飞书长连接启动失败", e);
            }
        }, "feishu-ws-card-callback");
        thread.setDaemon(true);
        thread.start();
        log.info("飞书审批卡片回调长连接已启动");
    }

    /**
     * 卡片回调处理：解析按钮回传数据 → 操作人身份校验 → 重复点击拦截 → 驱动审批 → 返回更新后的卡片与操作提示
     */
    private P2CardActionTriggerResponse onCardActionTrigger(P2CardActionTrigger event) {
        try {
            JsonNode data = objectMapper.readTree(Jsons.DEFAULT.toJson(event.getEvent()));
            JsonNode value = data.path("action").path("value");
            String taskId = text(value, ACTION_TASK_ID);
            Long approverEmpId = longValue(value, ACTION_APPROVER_EMP_ID);
            String approverOpenId = text(value, ACTION_APPROVER_OPEN_ID);
            String procInstId = text(value, ACTION_PROC_INST_ID);
            String taskName = text(value, ACTION_TASK_NAME);
            boolean approved = booleanValue(value, ACTION_APPROVED);

            if (taskId == null || approverEmpId == null) {
                log.warn("飞书卡片回调缺少 taskId/审批人，忽略 data={}", data);
                return response("error", "回调数据不完整，无法处理", null);
            }
            JsonNode approvedNode = value.path(ACTION_APPROVED);
            if (approvedNode.isMissingNode() || approvedNode.isNull()) {
                log.warn("飞书卡片回调缺少审批结果，忽略 taskId={}", taskId);
                return response("error", "回调数据不完整，无法处理", null);
            }

            // 操作人身份校验：卡片可能被转发，只有按钮绑定的审批人本人才能操作
            String operatorOpenId = text(data.path("operator"), "open_id");
            if (operatorOpenId != null && approverOpenId != null && !approverOpenId.equals(operatorOpenId)) {
                log.warn("飞书卡片操作人与审批人不一致 taskId={}, approverEmpId={}, operatorOpenId={}",
                        taskId, approverEmpId, operatorOpenId);
                return response("error", "仅审批人本人可操作该卡片", null);
            }
            if (approverOpenId == null) {
                // 兼容升级前已发送的旧卡片（按钮未内嵌 open_id），仅记录日志并依赖任务归属校验兜底
                log.warn("卡片缺少 approverOpenId，跳过操作人校验 taskId={}", taskId);
            }

            // 重复点击拦截：任务已被处理时不再驱动审批，返回「已处理」卡片并提示
            if (!flowOrchestratorService.isTaskPending(taskId)) {
                log.info("飞书卡片重复点击，任务已处理 taskId={}, approverEmpId={}", taskId, approverEmpId);
                return response("warning", "该审批已处理",
                        pushService.buildAlreadyHandledCardJson(procInstId, taskName));
            }

            TaskCompleteDto dto = new TaskCompleteDto();
            dto.setApproved(approved);
            dto.setComment("飞书卡片审批");
            flowOrchestratorService.completeTaskAs(taskId, approverEmpId, dto);
            log.info("飞书卡片审批完成 taskId={}, approverEmpId={}, approved={}", taskId, approverEmpId, approved);

            // 返回结果卡片（去掉按钮）替换原卡片，禁止再次点击
            String card = pushService.buildCompletedCardJson(procInstId, taskName, approved);
            return response("success", approved ? "已同意" : "已拒绝", card);
        } catch (BusinessException e) {
            log.warn("飞书卡片审批被拒绝 code={}, msg={}", e.getCode(), e.getMsg());
            String hint = Objects.equals(e.getCode(), ErrorCode.FLOW_TASK_NOT_ASSIGNEE.getCode())
                    ? "该审批不属于当前操作人" : e.getMsg();
            return response("error", hint, null);
        } catch (Exception e) {
            // 回调处理失败仅提示用户，具体原因记录日志（3 秒内必须响应）
            log.error("飞书卡片回调处理失败", e);
            return response("error", "处理失败：" + e.getMessage(), null);
        }
    }

    /**
     * 组装回调响应：toast 提示 +（可选）替换后的卡片。
     *
     * @param cardJson 新的卡片 JSON；为 null 时不更新卡片
     */
    private P2CardActionTriggerResponse response(String type, String content, String cardJson) {
        P2CardActionTriggerResponse response = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();
        toast.setType(type);
        toast.setContent(truncate(content, TOAST_MAX_LENGTH));
        response.setToast(toast);
        if (cardJson != null) {
            CallBackCard card = new CallBackCard();
            // 官方要求卡片 JSON 使用 raw 类型，data 直接放卡片对象（而非字符串）
            card.setType("raw");
            card.setData(cardData(cardJson));
            response.setCard(card);
        }
        return response;
    }

    /** raw 类型要求 data 为卡片对象，故解析为 JsonNode */
    private JsonNode cardData(String cardJson) {
        try {
            return objectMapper.readTree(cardJson);
        } catch (Exception e) {
            throw new IllegalStateException("飞书卡片 JSON 解析失败", e);
        }
    }

    private String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return v == null || v.isBlank() ? null : v;
    }

    /**
     * 兼容数字与字符串两种回传形式：飞书在部分场景会把按钮 value 中的数字序列化为字符串，
     * 只按数值判断会解析失败（导致「按钮点击报错但后端逻辑正常」）。
     */
    private Long longValue(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isNumber()) {
            return v.asLong();
        }
        String raw = v.asText("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 兼容布尔与字符串（"true"/"false"）两种回传形式 */
    private boolean booleanValue(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isBoolean() ? v.asBoolean() : "true".equalsIgnoreCase(v.asText(""));
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }
}
