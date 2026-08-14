package org.example.hragent.converter;

import org.example.hragent.dto.ResumeSaveDto;
import org.example.hragent.dto.ResumeUpdateDto;
import org.example.hragent.entity.Resume;
import org.example.hragent.entity.Resume;
import org.example.hragent.vo.ResumeVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ResumeConverter {

    Resume saveDtoToEntity(ResumeSaveDto dto);

    void updateDtoToEntity(ResumeUpdateDto dto, @MappingTarget Resume entity);

    ResumeVO entityToVo(Resume entity);

    default List<ResumeVO> entityListToVoList(List<Resume> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}