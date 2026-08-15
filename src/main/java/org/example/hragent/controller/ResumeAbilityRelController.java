package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.converter.ResumeAbilityRelConverter;
import org.example.hragent.dto.ResumeAbilityRelQueryDto;
import org.example.hragent.dto.ResumeAbilityRelSaveDto;
import org.example.hragent.dto.ResumeAbilityRelUpdateDto;
import org.example.hragent.entity.ResumeAbilityRel;
import org.example.hragent.service.ResumeAbilityRelService;
import org.example.hragent.vo.ResumeAbilityRelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resume-ability-rels")
public class ResumeAbilityRelController extends BaseCrudController<ResumeAbilityRel, ResumeAbilityRelVO, ResumeAbilityRelQueryDto, ResumeAbilityRelSaveDto, ResumeAbilityRelUpdateDto> {

    @Autowired
    private ResumeAbilityRelService resumeAbilityRelService;

    @Autowired
    private ResumeAbilityRelConverter resumeAbilityRelConverter;

    @Override
    protected ResumeAbilityRelService baseService() {
        return resumeAbilityRelService;
    }

    @Override
    protected ResumeAbilityRelConverter baseConverter() {
        return resumeAbilityRelConverter;
    }

    @Override
    protected ResumeAbilityRelConverter fullConverter() {
        return resumeAbilityRelConverter;
    }

    @Override
    protected LambdaQueryWrapper<ResumeAbilityRel> buildWrapper(ResumeAbilityRelQueryDto queryDto) {
        LambdaQueryWrapper<ResumeAbilityRel> w = new LambdaQueryWrapper<>();
        w.eq(queryDto.getResumeId() != null, ResumeAbilityRel::getResumeId, queryDto.getResumeId())
         .eq(queryDto.getAbilityTagId() != null, ResumeAbilityRel::getAbilityTagId, queryDto.getAbilityTagId())
         .eq(queryDto.getSource() != null && !queryDto.getSource().isBlank(), ResumeAbilityRel::getSource, queryDto.getSource());
        return w;
    }
}
