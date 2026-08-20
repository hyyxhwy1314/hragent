package org.example.hragent.service;

import org.example.hragent.entity.JobPost;
import org.example.hragent.vo.PublicJobPostVO;

import java.util.List;

public interface JobPostService extends BaseService<JobPost> {

    /**
     * 查询对外开放的岗位列表（仅公开字段）
     */
    List<PublicJobPostVO> listPublic();

    /**
     * 查询单个对外开放岗位详情（仅公开字段）
     */
    PublicJobPostVO getPublicById(Long id);
}
