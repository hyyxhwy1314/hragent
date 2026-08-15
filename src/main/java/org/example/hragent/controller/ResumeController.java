package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.converter.ResumeConverter;
import org.example.hragent.dto.ResumeQueryDto;
import org.example.hragent.dto.ResumeSaveDto;
import org.example.hragent.dto.ResumeUpdateDto;
import org.example.hragent.entity.Resume;
import org.example.hragent.service.ResumeService;
import org.example.hragent.vo.ResumeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
