package org.example.hragent.converter;

import org.example.hragent.dto.ResumeAbilityRelSaveDto;
import org.example.hragent.dto.ResumeAbilityRelUpdateDto;
import org.example.hragent.entity.ResumeAbilityRel;
import org.example.hragent.vo.ResumeAbilityRelVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ResumeAbilityRelConverter
        extends BaseFullConverter<ResumeAbilityRel, ResumeAbilityRelVO, ResumeAbilityRelSaveDto, ResumeAbilityRelUpdateDto> {

    @Override
    ResumeAbilityRel saveDtoToEntity(ResumeAbilityRelSaveDto dto);

    @Override
    void updateDtoToEntity(ResumeAbilityRelUpdateDto dto, @MappingTarget ResumeAbilityRel entity);

    @Override
    ResumeAbilityRelVO entityToVo(ResumeAbilityRel entity);
}
