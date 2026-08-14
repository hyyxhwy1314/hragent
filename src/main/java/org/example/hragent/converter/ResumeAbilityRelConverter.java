package org.example.hragent.converter;

import org.example.hragent.dto.ResumeAbilityRelSaveDto;
import org.example.hragent.entity.ResumeAbilityRel;
import org.example.hragent.entity.ResumeAbilityRel;
import org.example.hragent.vo.ResumeAbilityRelVO;
import org.mapstruct.Mapper;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ResumeAbilityRelConverter {

    ResumeAbilityRel saveDtoToEntity(ResumeAbilityRelSaveDto dto);

    ResumeAbilityRelVO entityToVo(ResumeAbilityRel entity);

    default List<ResumeAbilityRelVO> entityListToVoList(List<ResumeAbilityRel> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}