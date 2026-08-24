package org.example.hragent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.service.ResumeAiAnalysisService;
import org.example.hragent.vo.ResumeAiAnalysisVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * 简历AI分析服务实现
 */
@Slf4j
@Service
public class ResumeAiAnalysisServiceImpl implements ResumeAiAnalysisService {

    @Value("${python.ai.service.url:http://127.0.0.1:8000}")
    private String pythonServiceUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ResumeAiAnalysisServiceImpl() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ResumeAiAnalysisVO analyzeResume(byte[] fileBytes, String filename) {
        try {
            // 准备multipart请求
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // 确保文件名有.pdf扩展名
            String finalFilename = filename != null ? filename : "resume";
            if (!finalFilename.toLowerCase().endsWith(".pdf")) {
                finalFilename += ".pdf";
            }
            
            final String resourceFilename = finalFilename;
            
            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return resourceFilename;
                }
            };
            body.add("file", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 调用Python服务
            String url = pythonServiceUrl + "/api/resume/analyze";
            log.info("调用Python AI分析服务: {}, 文件名: {}", url, finalFilename);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                ResumeAiAnalysisVO vo = new ResumeAiAnalysisVO();
                vo.setSuccess(jsonNode.path("success").asBoolean());
                vo.setFilename(jsonNode.path("filename").asText());
                vo.setResumeText(jsonNode.path("resume_text").asText());
                vo.setEvaluation(jsonNode.path("evaluation").asText());
                log.info("Python AI分析成功: {}", vo.getFilename());
                return vo;
            } else {
                log.error("Python AI分析失败: {}", response.getStatusCode());
                ResumeAiAnalysisVO errorVo = new ResumeAiAnalysisVO();
                errorVo.setSuccess(false);
                errorVo.setEvaluation("AI分析服务调用失败: " + response.getStatusCode());
                return errorVo;
            }
        } catch (IOException e) {
            log.error("调用Python AI分析服务IO异常", e);
            ResumeAiAnalysisVO errorVo = new ResumeAiAnalysisVO();
            errorVo.setSuccess(false);
            errorVo.setEvaluation("AI分析服务IO异常: " + e.getMessage());
            return errorVo;
        } catch (Exception e) {
            log.error("调用Python AI分析服务异常", e);
            ResumeAiAnalysisVO errorVo = new ResumeAiAnalysisVO();
            errorVo.setSuccess(false);
            errorVo.setEvaluation("AI分析服务异常: " + e.getMessage());
            return errorVo;
        }
    }
}