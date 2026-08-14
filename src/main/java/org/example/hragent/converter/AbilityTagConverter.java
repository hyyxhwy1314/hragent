package org.example.hragent.converter;

import org.example.hragent.dto.AbilityTagSaveDto;
import org.example.hragent.dto.AbilityTagUpdateDto;
import org.example.hragent.entity.TAbilityTag;
import org.example.hragent.vo.AbilityTagVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface AbilityTagConverter {

    TAbilityTag saveDtoToEntity(AbilityTagSaveDto dto);

    void updateDtoToEntity(AbilityTagUpdateDto dto, @MappingTarget TAbilityTag entity);

    AbilityTagVO entityToVo(TAbilityTag entity);

    default List<AbilityTagVO> entityListToVoList(List<TAbilityTag> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}