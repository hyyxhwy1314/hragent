package org.example.hragent.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.annotation.DistributedLock;
import org.example.hragent.annotation.LogAction;
import org.example.hragent.annotation.RateLimit;
import org.example.hragent.annotation.RepeatSubmit;
import org.example.hragent.dto.FlowStartDto;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.service.FlowOrchestratorService;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.vo.FlowInstanceVO;
import org.example.hragent.vo.R;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 流程定义与发起 Controller
 * <p>
 * 职责：
 * <ul>
 *   <li>部署 BPMN 流程定义</li>
 *   <li>查询已部署的流程定义</li>
 *   <li>发起流程实例</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/flow/process")
public class FlowProcessController {

    private final FlowOrchestratorService flowOrchestratorService;
    private final RepositoryService repositoryService;

    public FlowProcessController(FlowOrchestratorService flowOrchestratorService,
                                 RepositoryService repositoryService) {
        this.flowOrchestratorService = flowOrchestratorService;
        this.repositoryService = repositoryService;
    }

    /**
     * 部署 BPMN 流程定义
     * <p>
     * 上传 .bpmn20.xml 文件，引擎自动解析并部署。
     * 同名流程会自动升级版本号。
     */
    @PostMapping("/deploy")
    @LogAction(module = "流程审批", action = "部署流程")
    @RateLimit(rate = 5, rateInterval = 3, rateIntervalUnit = TimeUnit.SECONDS, message = "部署过于频繁，请稍后再试")
    public R<Map<String, Object>> deploy(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        Deployment deployment = repositoryService.createDeployment()
                .name(name)
                .addInputStream(name, file.getInputStream())
                .deploy();

        Map<String, Object> result = new HashMap<>();
        result.put("deploymentId", deployment.getId());
        result.put("name", deployment.getName());
        result.put("deployTime", deployment.getDeploymentTime());
        log.info("流程部署成功 deploymentId={}, name={}", deployment.getId(), deployment.getName());
        return R.ok(result);
    }

    /**
     * 查询已部署的流程定义列表（每个流程取最新版本）
     */
    @GetMapping("/definitions")
    public R<List<Map<String, Object>>> listDefinitions() {
        List<ProcessDefinition> defs = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey().asc()
                .list();

        List<Map<String, Object>> result = defs.stream().map(d -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId());
            m.put("key", d.getKey());
            m.put("name", d.getName());
            m.put("version", d.getVersion());
            m.put("deploymentId", d.getDeploymentId());
            return m;
        }).collect(Collectors.toList());
        return R.ok(result);
    }

    /**
     * 查询指定流程定义（最新版本）的 BPMN XML 内容，供前端设计器加载编辑
     */
    @GetMapping("/definitions/{processKey}/xml")
    public R<Map<String, Object>> getDefinitionXml(@PathVariable String processKey) throws IOException {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        BusinessException.throwIf(def == null, ErrorCode.FLOW_DEFINITION_NOT_FOUND,
                "流程定义不存在: " + processKey);

        Map<String, Object> result = new HashMap<>();
        result.put("key", def.getKey());
        result.put("name", def.getName());
        result.put("version", def.getVersion());
        try (InputStream in = repositoryService.getProcessModel(def.getId())) {
            result.put("bpmnXml", in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        return R.ok(result);
    }

    /**
     * 发起流程实例
     * <p>
     * - {@link RepeatSubmit} 防止用户重复点击多次发起
     * - {@link DistributedLock} 防止同业务ID并发发起（避免重复流程实例）
     */
    @PostMapping("/start")
    @LogAction(module = "流程审批", action = "发起流程")
    @RepeatSubmit(interval = 5, unit = TimeUnit.SECONDS, message = "请勿重复发起流程")
    @DistributedLock(key = "#dto.processKey + ':' + #dto.bizId", message = "流程发起中，请稍后")
    public R<FlowInstanceVO> start(@Valid @RequestBody FlowStartDto dto) {
        return R.ok(flowOrchestratorService.start(dto));
    }

    /**
     * 撤回流程（操作人从登录上下文取）
     */
    @PostMapping("/instances/{flowInstanceId}/cancel")
    @LogAction(module = "流程审批", action = "撤回流程")
    public R<Boolean> cancel(@PathVariable Long flowInstanceId) {
        Long operatorEmpId = CurrentUserService.empId();
        BusinessException.throwIf(operatorEmpId == null, ErrorCode.UNAUTHORIZED);
        return R.ok(flowOrchestratorService.cancel(flowInstanceId, operatorEmpId));
    }
}
