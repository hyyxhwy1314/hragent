package org.example.hragent.converter;

import org.example.hragent.dto.AbilityTagSaveDto;
import org.example.hragent.dto.AbilityTagUpdateDto;
import org.example.hragent.entity.AbilityTag;
import org.example.hragent.entity.AbilityTag;
import org.example.hragent.vo.AbilityTagVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface AbilityTagConverter {

    AbilityTag saveDtoToEntity(AbilityTagSaveDto dto);

    void updateDtoToEntity(AbilityTagUpdateDto dto, @MappingTarget AbilityTag entity);

    AbilityTagVO entityToVo(AbilityTag entity);

    default List<AbilityTagVO> entityListToVoList(List<AbilityTag> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}