package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.hragent.annotation.RateLimit;
import org.example.hragent.annotation.RepeatSubmit;
import org.example.hragent.converter.ResumeConverter;
import org.example.hragent.dto.ResumeQueryDto;
import org.example.hragent.dto.ResumeSaveDto;
import org.example.hragent.dto.ResumeUpdateDto;
import org.example.hragent.entity.Resume;
import org.example.hragent.service.ResumeService;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.vo.R;
import org.example.hragent.vo.ResumeParsedData;
import org.example.hragent.vo.ResumeUploadVO;
import org.example.hragent.vo.ResumeVO;
import org.example.hragent.vo.ResumeAiAnalysisVO;
import org.example.hragent.utils.RedisUtils;
import org.example.hragent.service.ResumeParserService;
import org.example.hragent.service.AliyunOcrService;
import org.example.hragent.service.FileService;
import org.example.hragent.service.ResumeAiAnalysisService;
import org.example.hragent.config.AliyunOcrProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * 简历 Controller
 * 投递接口属于公开接口，需要：
 * 1. @RateLimit  防止恶意刷接口
 * 2. @RepeatSubmit 防止用户重复点击多次提交
 */
@Slf4j
@RestController
@RequestMapping("/resumes")
public class ResumeController extends BaseCrudController<Resume, ResumeVO, ResumeQueryDto, ResumeSaveDto, ResumeUpdateDto> {

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ResumeConverter resumeConverter;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ResumeParserService resumeParserService;

    @Autowired
    private AliyunOcrService aliyunOcrService;

    @Autowired
    private AliyunOcrProperties aliyunOcrProperties;

    @Autowired
    private ResumeAiAnalysisService resumeAiAnalysisService;

    @Autowired
    private FileService fileService;

    /**
     * 清掉本地调试期间遗留在 Redis 的「限流 / 防重」键，避免改注解间隔后旧 TTL（5000 秒之类）
     * 还在生效导致测试永远提示"重复提交"。仅开发环境点一下。
     */
    @DeleteMapping("/debug/cache-keys")
    public R<Long> clearRateAndRepeatKeys() {
        java.util.Set<String> s1 = redisUtils.keys("repeat_submit:*");
        java.util.Set<String> s2 = redisUtils.keys("rate_limit:*");
        java.util.Set<String> all = new java.util.HashSet<>();
        if (s1 != null) all.addAll(s1);
        if (s2 != null) all.addAll(s2);
        long n = all.isEmpty() ? 0 : redisUtils.delete(all);
        return R.ok(n);
    }

    /**
     * AOP 诊断：判断当前 Controller 是否被 CGLIB 代理
     * （被代理则类名含 $$EnhancerBySpringCGLIB$$）
     */
    @GetMapping("/debug/aop")
    public R<Map<String, Object>> debugAop() {
        Map<String, Object> result = new HashMap<>();
        result.put("thisClass", this.getClass().getName());
        result.put("thisIsProxy", this.getClass().getName().contains("$"));
        Object bean = applicationContext.getBean("resumeController");
        result.put("beanClass", bean.getClass().getName());
        result.put("beanIsProxy", bean.getClass().getName().contains("$"));
        // 判断 aspect bean 是否存在
        boolean hasRL = applicationContext.containsBean("rateLimitAspect");
        boolean hasDL = applicationContext.containsBean("distributedLockAspect");
        boolean hasRS = applicationContext.containsBean("repeatSubmitAspect");
        Map<String, Object> aspects = new HashMap<>();
        aspects.put("rateLimitAspect", hasRL ? applicationContext.getBean("rateLimitAspect").getClass().getName() : "NOT FOUND");
        aspects.put("distributedLockAspect", hasDL ? applicationContext.getBean("distributedLockAspect").getClass().getName() : "NOT FOUND");
        aspects.put("repeatSubmitAspect", hasRS ? applicationContext.getBean("repeatSubmitAspect").getClass().getName() : "NOT FOUND");
        result.put("aspectBeans", aspects);
        return R.ok(result);
    }

    @Override
    protected ResumeService baseService() {
        return resumeService;
    }

    @Override
    protected ResumeConverter baseConverter() {
        return resumeConverter;
    }

    @Override
    protected ResumeConverter fullConverter() {
        return resumeConverter;
    }

    @Override
    protected LambdaQueryWrapper<Resume> buildWrapper(ResumeQueryDto queryDto) {
        LambdaQueryWrapper<Resume> w = new LambdaQueryWrapper<>();
        w.like(queryDto.getResumeName() != null && !queryDto.getResumeName().isBlank(), Resume::getResumeName, queryDto.getResumeName())
         .eq(queryDto.getResumeStatus() != null, Resume::getResumeStatus, queryDto.getResumeStatus())
         .eq(queryDto.getTargetJobId() != null, Resume::getTargetJobId, queryDto.getTargetJobId())
         .eq(queryDto.getOwnerEmpId() != null, Resume::getOwnerEmpId, queryDto.getOwnerEmpId())
         .ge(queryDto.getMinMatchScore() != null, Resume::getMatchScore, queryDto.getMinMatchScore());
        return w;
    }

    /**
     * 简历投递接口
     * - @RateLimit: 每 3 秒允许 5 个请求（按 IP 维度）
     * - @RepeatSubmit: 5 秒内相同参数视为重复提交
     */
    @Override
    @PostMapping
    @RateLimit(rate = 5, rateInterval = 3, rateIntervalUnit = TimeUnit.SECONDS, message = "投递过于频繁，请稍后再试")
    @RepeatSubmit(interval = 5, unit = TimeUnit.SECONDS, message = "简历已提交，请勿重复投递")
    public R<ResumeVO> save(@Valid @RequestBody ResumeSaveDto saveDto) {
        return super.save(saveDto);
    }

    /**
     * 简历附件上传
     * 返回文件ID(resumeFileId)、预览URL与解析出的简历字段，前端据此回填表单
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ResumeUploadVO> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(resumeService.uploadResumeFile(file));
    }

    /**
     * 调试接口：仅解析文件，不上传 COS，方便定位 OCR/PDFBox 解析结果
     */
    @PostMapping(value = "/debug/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ResumeParsedData> debugParse(@RequestParam("file") MultipartFile file) {
        byte[] data = new byte[0];
        try { data = file.getBytes(); } catch (Exception e) { /* ignore */ }
        ResumeParsedData parsed = resumeParserService.parse(data, file.getOriginalFilename(), file.getContentType());
        return R.ok(parsed);
    }

    /**
     * 调试接口：查看 OCR 配置状态 + 直接调用 OCR 识别并返回原始错误信息
     */
    @PostMapping(value = "/debug/ocr-raw", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, Object>> debugOcrRaw(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        result.put("ocrEnabled", aliyunOcrProperties.isEnabled());
        result.put("endpoint", aliyunOcrProperties.getEndpoint());
        result.put("regionId", aliyunOcrProperties.getRegionId());
        result.put("akPrefix", aliyunOcrProperties.getAccessKeyId() == null ? "null" :
                aliyunOcrProperties.getAccessKeyId().substring(0, Math.min(8, aliyunOcrProperties.getAccessKeyId().length())) + "***");

        byte[] data = new byte[0];
        try { data = file.getBytes(); } catch (Exception e) { result.put("readError", e.getMessage()); }

        try {
            String ocrText = aliyunOcrService.recognizeRawText(data, file.getOriginalFilename(), file.getContentType());
            result.put("ocrRawText", ocrText);
            result.put("ocrTextLength", ocrText == null ? 0 : ocrText.length());
        } catch (Exception e) {
            result.put("ocrError", e.getClass().getName() + ": " + e.getMessage());
            // Print full stack trace elements
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            result.put("ocrStackTrace", sw.toString().substring(0, Math.min(2000, sw.toString().length())));
        }
        // 调试增强：直接暴露阿里云 RecognizeAllText 的完整响应字段，定位 content 为空的根因
        try {
            result.put("ocrDebug", aliyunOcrService.recognizeAllTextDebug(data));
        } catch (Exception e) {
            result.put("ocrDebugError", e.getClass().getName() + ": " + e.getMessage());
        }
        return R.ok(result);
    }

    /**
     * 简历附件预览（返回预签名URL，前端直接打开）
     */
    @GetMapping("/{id}/file/preview")
    public R<Map<String, String>> previewFile(@PathVariable Long id) {
        Map<String, String> result = new HashMap<>();
        result.put("previewUrl", resumeService.getFilePreviewUrl(id));
        return R.ok(result);
    }

    /**
     * 简历附件下载（直接输出文件流）
     * 业务异常（简历不存在/未上传附件）会向上抛出，由全局异常处理器返回 JSON
     */
    @GetMapping("/{id}/file/download")
    public void downloadFile(@PathVariable Long id, HttpServletResponse response) throws java.io.IOException {
        OutputStream out = response.getOutputStream();
        String fileName = resumeService.downloadResumeFile(id, out);
        String encoded = URLEncoder.encode(fileName == null ? "resume" : fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"");
        out.flush();
    }

    /**
     * 简历归档
     */
    @PutMapping("/{id}/archive")
    public R<Boolean> archive(@PathVariable Long id) {
        return R.ok(resumeService.archive(id));
    }

    /**
     * 简历AI分析
     * 调用Python服务进行简历AI分析
     */
    @PostMapping("/{id}/ai-analyze")
    public R<ResumeAiAnalysisVO> aiAnalyze(@PathVariable Long id) {
        log.info("收到AI分析请求，简历ID: {}", id);
        Resume resume = resumeService.getByIdChecked(id);
        BusinessException.throwIf(resume.getResumeFileId() == null, ErrorCode.PARAM_ERROR, "该简历未上传附件");
        
        log.info("开始下载文件，文件ID: {}", resume.getResumeFileId());
        // 从文件服务获取文件字节
        byte[] fileBytes = fileService.downloadBytes(resume.getResumeFileId());
        BusinessException.throwIf(fileBytes == null || fileBytes.length == 0, ErrorCode.OPERATION_FAILED, "文件下载失败");
        
        log.info("文件下载成功，大小: {} bytes, 开始调用Python服务", fileBytes.length);
        // 直接调用分析服务，传递字节数组和文件名
        ResumeAiAnalysisVO result = resumeAiAnalysisService.analyzeResume(fileBytes, resume.getResumeName());
        log.info("Python服务调用完成，结果: {}", result.getSuccess());
        return R.ok(result);
    }
}