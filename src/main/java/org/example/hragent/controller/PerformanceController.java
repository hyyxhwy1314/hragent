package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.converter.PerformanceConverter;
import org.example.hragent.dto.PerformanceQueryDto;
import org.example.hragent.dto.PerformanceSaveDto;
import org.example.hragent.dto.PerformanceUpdateDto;
import org.example.hragent.entity.Performance;
import org.example.hragent.service.PerformanceService;
import org.example.hragent.vo.PerformanceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/performances")
public class PerformanceController extends BaseCrudController<Performance, PerformanceVO, PerformanceQueryDto, PerformanceSaveDto, PerformanceUpdateDto> {

    @Autowired
    private PerformanceService performanceService;

    @Autowired
    private PerformanceConverter performanceConverter;

    @Override
    protected PerformanceService baseService() {
        return performanceService;
    }

    @Override
    protected PerformanceConverter baseConverter() {
        return performanceConverter;
    }

    @Override
    protected PerformanceConverter fullConverter() {
        return performanceConverter;
    }

    @Override
    protected LambdaQueryWrapper<Performance> buildWrapper(PerformanceQueryDto queryDto) {
        LambdaQueryWrapper<Performance> w = new LambdaQueryWrapper<>();
        w.eq(queryDto.getEmpId() != null, Performance::getEmpId, queryDto.getEmpId())
         .like(queryDto.getPeriodCode() != null && !queryDto.getPeriodCode().isBlank(), Performance::getPeriodCode, queryDto.getPeriodCode())
         .eq(queryDto.getStatus() != null, Performance::getStatus, queryDto.getStatus());
        return w;
    }
}
