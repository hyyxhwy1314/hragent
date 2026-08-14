package org.example.hragent.converter;

import org.example.hragent.dto.JobPostSaveDto;
import org.example.hragent.dto.JobPostUpdateDto;
import org.example.hragent.entity.TJobPost;
import org.example.hragent.vo.JobPostVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface JobPostConverter {

    TJobPost saveDtoToEntity(JobPostSaveDto dto);

    void updateDtoToEntity(JobPostUpdateDto dto, @MappingTarget TJobPost entity);

    JobPostVO entityToVo(TJobPost entity);

    default List<JobPostVO> entityListToVoList(List<TJobPost> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}