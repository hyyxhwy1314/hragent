package org.example.hragent.converter;

import org.example.hragent.dto.ResumeSaveDto;
import org.example.hragent.dto.ResumeUpdateDto;
import org.example.hragent.entity.TResume;
import org.example.hragent.vo.ResumeVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ResumeConverter {

    TResume saveDtoToEntity(ResumeSaveDto dto);

    void updateDtoToEntity(ResumeUpdateDto dto, @MappingTarget TResume entity);

    ResumeVO entityToVo(TResume entity);

    default List<ResumeVO> entityListToVoList(List<TResume> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}