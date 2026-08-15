package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.converter.JobPostConverter;
import org.example.hragent.dto.JobPostQueryDto;
import org.example.hragent.dto.JobPostSaveDto;
import org.example.hragent.dto.JobPostUpdateDto;
import org.example.hragent.entity.JobPost;
import org.example.hragent.service.JobPostService;
import org.example.hragent.vo.JobPostVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/job-posts")
public class JobPostController extends BaseCrudController<JobPost, JobPostVO, JobPostQueryDto, JobPostSaveDto, JobPostUpdateDto> {

    @Autowired
    private JobPostService jobPostService;

    @Autowired
    private JobPostConverter jobPostConverter;

    @Override
    protected JobPostService baseService() {
        return jobPostService;
    }

    @Override
    protected JobPostConverter baseConverter() {
        return jobPostConverter;
    }

    @Override
    protected JobPostConverter fullConverter() {
        return jobPostConverter;
    }

    @Override
    protected LambdaQueryWrapper<JobPost> buildWrapper(JobPostQueryDto queryDto) {
        LambdaQueryWrapper<JobPost> w = new LambdaQueryWrapper<>();
        w.like(queryDto.getJobCode() != null && !queryDto.getJobCode().isBlank(), JobPost::getJobCode, queryDto.getJobCode())
         .like(queryDto.getJobName() != null && !queryDto.getJobName().isBlank(), JobPost::getJobName, queryDto.getJobName())
         .like(queryDto.getDeptName() != null && !queryDto.getDeptName().isBlank(), JobPost::getDeptName, queryDto.getDeptName())
         .eq(queryDto.getJobStatus() != null, JobPost::getJobStatus, queryDto.getJobStatus());
        return w;
    }
}
