package org.example.hragent.service.impl;

import org.example.hragent.entity.JobPost;
import org.example.hragent.mapper.JobPostMapper;
import org.example.hragent.service.JobPostService;
import org.springframework.stereotype.Service;

@Service
public class JobPostServiceImpl extends BaseServiceImpl<JobPostMapper, JobPost> implements JobPostService {
}
