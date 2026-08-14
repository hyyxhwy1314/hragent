package org.example.hragent.converter;

import org.example.hragent.dto.JobPostSaveDto;
import org.example.hragent.dto.JobPostUpdateDto;
import org.example.hragent.entity.JobPost;
import org.example.hragent.entity.JobPost;
import org.example.hragent.vo.JobPostVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface JobPostConverter {

    JobPost saveDtoToEntity(JobPostSaveDto dto);

    void updateDtoToEntity(JobPostUpdateDto dto, @MappingTarget JobPost entity);

    JobPostVO entityToVo(JobPost entity);

    default List<JobPostVO> entityListToVoList(List<JobPost> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}