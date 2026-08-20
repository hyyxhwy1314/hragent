package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.entity.JobPost;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.JobPostMapper;
import org.example.hragent.service.JobPostService;
import org.example.hragent.vo.PublicJobPostVO;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 岗位 Service 实现
 * 招聘门户频繁查询岗位详情，仅 getById 缓存
 * 列表因筛选条件多变不缓存
 */
@Service
public class JobPostServiceImpl extends BaseServiceImpl<JobPostMapper, JobPost> implements JobPostService {

    @Override
    @Cacheable(value = "job_post", key = "#id", unless = "#result == null")
    public JobPost getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = "job_post", allEntries = true)
    public boolean save(JobPost entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(value = "job_post", allEntries = true)
    public boolean updateById(JobPost entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(value = "job_post", allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }

    @Override
    @Cacheable(value = "job_post", key = "'public_list'", unless = "#result == null || #result.isEmpty()")
    public List<PublicJobPostVO> listPublic() {
        LambdaQueryWrapper<JobPost> wrapper = new LambdaQueryWrapper<JobPost>()
                .eq(JobPost::getIsPublic, 1)
                .eq(JobPost::getJobStatus, 1)
                .orderByDesc(JobPost::getPublishTime);
        List<JobPost> list = this.list(wrapper);
        return list.stream().map(this::toPublicVO).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "job_post", key = "'public_' + #id", unless = "#result == null")
    public PublicJobPostVO getPublicById(Long id) {
        JobPost post = this.getById(id);
        BusinessException.throwIf(post == null, ErrorCode.JOB_POST_NOT_EXIST);
        BusinessException.throwIf(post.getIsPublic() == null || post.getIsPublic() != 1,
                ErrorCode.NO_PERMISSION, "该岗位未对外发布");
        return toPublicVO(post);
    }

    private PublicJobPostVO toPublicVO(JobPost post) {
        if (post == null) {
            return null;
        }
        PublicJobPostVO vo = new PublicJobPostVO();
        vo.setId(post.getId());
        vo.setJobName(post.getJobName());
        vo.setJobDuty(post.getJobDuty());
        vo.setJobRequirement(post.getJobRequirement());
        vo.setWorkCity(post.getWorkCity());
        vo.setSalaryMin(post.getSalaryMin());
        vo.setSalaryMax(post.getSalaryMax());
        vo.setEducationReq(post.getEducationReq());
        vo.setWorkYearReq(post.getWorkYearReq());
        vo.setHeadCount(post.getHeadCount());
        vo.setPublishTime(post.getPublishTime());
        vo.setCloseTime(post.getCloseTime());
        return vo;
    }
}
