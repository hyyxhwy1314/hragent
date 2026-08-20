package org.example.hragent.controller;

import org.example.hragent.service.JobPostService;
import org.example.hragent.vo.PublicJobPostVO;
import org.example.hragent.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开岗位 Controller
 * 面向外部招聘门户，仅返回对外开放的岗位名称与描述，不暴露内部数据
 */
@RestController
@RequestMapping("/public/job-posts")
public class PublicJobPostController {

    @Autowired
    private JobPostService jobPostService;

    /**
     * 公开岗位列表（仅对外开放且招聘中的岗位）
     */
    @GetMapping
    public R<List<PublicJobPostVO>> list() {
        return R.ok(jobPostService.listPublic());
    }

    /**
     * 公开岗位详情
     */
    @GetMapping("/{id}")
    public R<PublicJobPostVO> getById(@PathVariable Long id) {
        return R.ok(jobPostService.getPublicById(id));
    }
}
