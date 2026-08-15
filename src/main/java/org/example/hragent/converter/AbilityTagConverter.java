package org.example.hragent.converter;

import org.example.hragent.dto.AbilityTagSaveDto;
import org.example.hragent.dto.AbilityTagUpdateDto;
import org.example.hragent.entity.AbilityTag;
import org.example.hragent.vo.AbilityTagVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AbilityTagConverter extends BaseFullConverter<AbilityTag, AbilityTagVO, AbilityTagSaveDto, AbilityTagUpdateDto> {

    @Override
    AbilityTag saveDtoToEntity(AbilityTagSaveDto dto);

    @Override
    void updateDtoToEntity(AbilityTagUpdateDto dto, @MappingTarget AbilityTag entity);

    @Override
    AbilityTagVO entityToVo(AbilityTag entity);
}
