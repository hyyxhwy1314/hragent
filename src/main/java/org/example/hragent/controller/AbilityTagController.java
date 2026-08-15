package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.converter.AbilityTagConverter;
import org.example.hragent.dto.AbilityTagQueryDto;
import org.example.hragent.dto.AbilityTagSaveDto;
import org.example.hragent.dto.AbilityTagUpdateDto;
import org.example.hragent.entity.AbilityTag;
import org.example.hragent.service.AbilityTagService;
import org.example.hragent.vo.AbilityTagVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ability-tags")
public class AbilityTagController extends BaseCrudController<AbilityTag, AbilityTagVO, AbilityTagQueryDto, AbilityTagSaveDto, AbilityTagUpdateDto> {

    @Autowired
    private AbilityTagService abilityTagService;

    @Autowired
    private AbilityTagConverter abilityTagConverter;

    @Override
    protected AbilityTagService baseService() {
        return abilityTagService;
    }

    @Override
    protected AbilityTagConverter baseConverter() {
        return abilityTagConverter;
    }

    @Override
    protected AbilityTagConverter fullConverter() {
        return abilityTagConverter;
    }

    @Override
    protected LambdaQueryWrapper<AbilityTag> buildWrapper(AbilityTagQueryDto queryDto) {
        LambdaQueryWrapper<AbilityTag> w = new LambdaQueryWrapper<>();
        w.like(queryDto.getTagName() != null && !queryDto.getTagName().isBlank(), AbilityTag::getTagName, queryDto.getTagName())
         .eq(queryDto.getTagCategory() != null && !queryDto.getTagCategory().isBlank(), AbilityTag::getTagCategory, queryDto.getTagCategory())
         .eq(queryDto.getStatus() != null, AbilityTag::getStatus, queryDto.getStatus());
        return w;
    }
}
