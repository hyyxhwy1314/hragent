package org.example.hragent.converter;

import org.example.hragent.dto.ResumeAbilityRelSaveDto;
import org.example.hragent.entity.TResumeAbilityRel;
import org.example.hragent.vo.ResumeAbilityRelVO;
import org.mapstruct.Mapper;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ResumeAbilityRelConverter {

    TResumeAbilityRel saveDtoToEntity(ResumeAbilityRelSaveDto dto);

    ResumeAbilityRelVO entityToVo(TResumeAbilityRel entity);

    default List<ResumeAbilityRelVO> entityListToVoList(List<TResumeAbilityRel> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}