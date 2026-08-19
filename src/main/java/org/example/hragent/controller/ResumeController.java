package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.example.hragent.annotation.RateLimit;
import org.example.hragent.annotation.RepeatSubmit;
import org.example.hragent.converter.ResumeConverter;
import org.example.hragent.dto.ResumeQueryDto;
import org.example.hragent.dto.ResumeSaveDto;
import org.example.hragent.dto.ResumeUpdateDto;
import org.example.hragent.entity.Resume;
import org.example.hragent.service.ResumeService;
import org.example.hragent.vo.R;
import org.example.hragent.vo.ResumeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * 简历 Controller
 * 投递接口属于公开接口，需要：
 * 1. @RateLimit  防止恶意刷接口
 * 2. @RepeatSubmit 防止用户重复点击多次提交
 */
@RestController
@RequestMapping("/resumes")
public class ResumeController extends BaseCrudController<Resume, ResumeVO, ResumeQueryDto, ResumeSaveDto, ResumeUpdateDto> {

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ResumeConverter resumeConverter;

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
}
