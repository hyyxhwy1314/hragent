package org.example.hragent.service.impl;

import org.example.hragent.entity.Performance;
import org.example.hragent.mapper.PerformanceMapper;
import org.example.hragent.service.PerformanceService;
import org.springframework.stereotype.Service;

@Service
public class PerformanceServiceImpl extends BaseServiceImpl<PerformanceMapper, Performance> implements PerformanceService {
}
