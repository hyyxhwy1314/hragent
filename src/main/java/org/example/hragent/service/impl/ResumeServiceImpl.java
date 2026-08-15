package org.example.hragent.service.impl;

import org.example.hragent.entity.Resume;
import org.example.hragent.mapper.ResumeMapper;
import org.example.hragent.service.ResumeService;
import org.springframework.stereotype.Service;

@Service
public class ResumeServiceImpl extends BaseServiceImpl<ResumeMapper, Resume> implements ResumeService {
}
