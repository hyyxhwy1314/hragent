package org.example.hragent.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.annotation.LogAction;
import org.example.hragent.entity.system.BehaviorLog;
import org.example.hragent.mapper.agent.AgentInteractLogMapper;
import org.example.hragent.service.BehaviorLogService;
import org.example.hragent.utils.CurrentUserService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行为日志拦截器
 * <p>
 * 全局采集所有用户操作行为：在请求完成后，根据请求路径与 HTTP 方法
 * 推断业务模块与动作，结合 {@link LogAction} 注解增强语义，异步写入行为日志。
 * <p>
 * 采集范围规则：
 * <ul>
 *   <li>仅采集已登录用户的业务请求（有效 controller 方法）</li>
 *   <li>记录 <b>所有</b> 操作（含 GET 查询、AI 交互、流程审批等），不做方法过滤</li>
 *   <li>看板自查询（/dashboard/system）不落库，避免日志刷屏</li>
 * </ul>
 */
@Slf4j
@Component
public class BehaviorLogInterceptor implements HandlerInterceptor {

    /** 不采集的路径前缀 */
    private static final List<String> EXCLUDE_PREFIXES = List.of("/dashboard/system");
    private static final List<String> EXCLUDE_METHODS = List.of("OPTIONS");

    /** URI 片段 -> 中文名（用于生成可读摘要） */
    private static final Map<String, String> SEGMENT_CN = new LinkedHashMap<>();
    /** 操作尾段 -> 中文动词（用于生成可读摘要） */
    private static final Map<String, String> VERB_CN = new LinkedHashMap<>();

    static {
        SEGMENT_CN.put("employee", "员工");
        SEGMENT_CN.put("resume", "简历");
        SEGMENT_CN.put("job", "岗位");
        SEGMENT_CN.put("performance", "绩效");
        SEGMENT_CN.put("training-course", "课程");
        SEGMENT_CN.put("course", "课程");
        SEGMENT_CN.put("contract", "合同");
        SEGMENT_CN.put("file", "文件");
        SEGMENT_CN.put("ability-tag", "能力标签");
        SEGMENT_CN.put("flow", "流程");
        SEGMENT_CN.put("process", "流程");
        SEGMENT_CN.put("self-service", "自助服务");
        SEGMENT_CN.put("agent", "AI");
        SEGMENT_CN.put("auth", "账号");
        SEGMENT_CN.put("chat", "对话");
        SEGMENT_CN.put("leave-request", "请假");
        SEGMENT_CN.put("certificate-request", "证明");

        VERB_CN.put("page", "列表");
        VERB_CN.put("list", "列表");
        VERB_CN.put("detail", "详情");
        VERB_CN.put("info", "详情");
        VERB_CN.put("add", "新增");
        VERB_CN.put("create", "新增");
        VERB_CN.put("save", "保存");
        VERB_CN.put("update", "更新");
        VERB_CN.put("edit", "编辑");
        VERB_CN.put("delete", "删除");
        VERB_CN.put("remove", "删除");
        VERB_CN.put("upload", "上传");
        VERB_CN.put("export", "导出");
        VERB_CN.put("start", "发起");
        VERB_CN.put("submit", "提交");
        VERB_CN.put("apply", "申请");
        VERB_CN.put("approve", "审批通过");
        VERB_CN.put("reject", "审批拒绝");
        VERB_CN.put("cancel", "取消");
        VERB_CN.put("withdraw", "撤回");
    }

    private final BehaviorLogService behaviorLogService;
    private final AgentInteractLogMapper interactLogMapper;

    public BehaviorLogInterceptor(BehaviorLogService behaviorLogService, AgentInteractLogMapper interactLogMapper) {
        this.behaviorLogService = behaviorLogService;
        this.interactLogMapper = interactLogMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("_startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            CurrentUserService.CurrentUser user = CurrentUserService.get();
            if (user == null || user.getEmpId() == null) {
                return; // 未登录不采集
            }
            String method = request.getMethod();
            if (EXCLUDE_METHODS.contains(method)) {
                return;
            }
            String uri = request.getRequestURI();
            if (EXCLUDE_PREFIXES.stream().anyMatch(uri::startsWith)) {
                return; // 看板自查询不落库
            }

            // 仅对 HandlerMethod 解析注解；否则（如资源请求）跳过
            LogAction meta = null;
            if (handler instanceof HandlerMethod hm) {
                Method m = hm.getMethod();
                if (m.isAnnotationPresent(LogAction.class)) {
                    meta = m.getAnnotation(LogAction.class);
                }
            }

            // 推断模块与动作
            String[] mv = resolveModuleAction(uri, method, meta);

            BehaviorLog bl = new BehaviorLog();
            bl.setUserId(user.getEmpId());
            bl.setUsername(user.getEmpName());
            bl.setModule(mv[0]);
            bl.setAction(mv[1]);
            bl.setSummary(summaryOf(uri, method));
            bl.setHttpMethod(method);
            bl.setRequestUri(uri);
            bl.setStatus(ex == null ? "success" : "fail");
            bl.setDurationMs(System.currentTimeMillis() - (Long) request.getAttribute("_startTime"));
            // AI 类操作：把本次交互产生的 Token 消耗关联进行为日志
            if (meta != null && meta.markToken()) {
                bl.setTokenCount(queryTokenCount(user.getEmpId()));
            } else {
                bl.setTokenCount(0);
            }

            behaviorLogService.record(bl);
        } catch (Exception e) {
            // 记录失败绝不能影响业务
            log.debug("行为日志采集异常: {}", e.getMessage());
        }
    }

    /** 查询指定用户最近一次 AI 交互的 Token 总量（赌注：刚完成的对话即为该行） */
    private int queryTokenCount(Long userId) {
        try {
            Map<String, Object> row = interactLogMapper.statLatestTokenByUser(userId);
            if (row == null || row.get("tokens") == null) return 0;
            return ((Number) row.get("tokens")).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 根据注解优先、其次路径推断模块/动作（所有方法均记录） */
    private String[] resolveModuleAction(String uri, String method, LogAction meta) {
        if (meta != null && StringUtils.hasText(meta.module())) {
            String action = StringUtils.hasText(meta.action()) ? meta.action() : defaultAction(method);
            return new String[]{meta.module(), action};
        }
        return new String[]{pathModule(uri), defaultAction(method)};
    }

    private String pathModule(String uri) {
        if (uri.contains("/resume")) return "简历分析";
        if (uri.contains("/agent")) return "AI交互";
        if (uri.contains("/flow")) return "流程审批";
        if (uri.contains("/employee")) return "员工管理";
        if (uri.contains("/self-service")) return "员工自助";
        if (uri.contains("/job")) return "岗位管理";
        if (uri.contains("/training") || uri.contains("/course")) return "培训管理";
        if (uri.contains("/performance")) return "绩效管理";
        if (uri.contains("/contract")) return "合同审阅";
        if (uri.contains("/file") || uri.contains("/upload")) return "文件管理";
        return "业务操作";
    }

    private String defaultAction(String method) {
        return switch (method) {
            case "POST" -> "新增";
            case "PUT", "PATCH" -> "更新";
            case "DELETE" -> "删除";
            case "GET" -> "查询";
            default -> "操作";
        };
    }

    /**
     * 生成可读的中文摘要，如「查询员工列表」「上传简历」「发起流程」，
     * 避免直接展示接口路径
     */
    private String summaryOf(String uri, String method) {
        String[] segs = uri.split("/");
        if (segs.length < 2) {
            return switch (method) {
                case "POST" -> "新增操作";
                case "PUT", "PATCH" -> "更新操作";
                case "DELETE" -> "删除操作";
                default -> "查询操作";
            };
        }
        String last = segs[segs.length - 1];
        String resource = "";
        for (int i = segs.length - 2; i >= 1; i--) {
            if (SEGMENT_CN.containsKey(segs[i])) {
                resource = SEGMENT_CN.get(segs[i]);
                break;
            }
        }
        // 尾段为动词：动词 + 资源（如 page -> 查询员工列表）
        if (VERB_CN.containsKey(last)) {
            String verb = switch (method) {
                case "POST" -> "新增";
                case "PUT", "PATCH" -> "更新";
                case "DELETE" -> "删除";
                default -> "查询";
            };
            // 动词化尾段优先（approve/reject/start 等语义更准确）
            if ("page".equals(last) || "list".equals(last)) {
                return verb + (resource.isEmpty() ? "数据" : resource) + "列表";
            }
            if ("detail".equals(last) || "info".equals(last)) {
                return verb + (resource.isEmpty() ? "数据" : resource) + "详情";
            }
            return VERB_CN.get(last) + (resource.isEmpty() ? "数据" : resource);
        }
        // 尾段为资源名：动词 + 资源（尾段无法识别时，退回用资源名）
        String actionVerb = switch (method) {
            case "POST" -> "新增";
            case "PUT", "PATCH" -> "更新";
            case "DELETE" -> "删除";
            default -> "查看";
        };
        String cn = SEGMENT_CN.getOrDefault(last, last);
        if (!resource.isEmpty()) {
            return actionVerb + resource;
        }
        return actionVerb + (cn.isEmpty() ? "数据" : cn);
    }
}