package org.example.hragent.converter;

import org.example.hragent.dto.JobPostSaveDto;
import org.example.hragent.dto.JobPostUpdateDto;
import org.example.hragent.entity.JobPost;
import org.example.hragent.vo.JobPostVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface JobPostConverter extends BaseFullConverter<JobPost, JobPostVO, JobPostSaveDto, JobPostUpdateDto> {

    @Override
    JobPost saveDtoToEntity(JobPostSaveDto dto);

    @Override
    void updateDtoToEntity(JobPostUpdateDto dto, @MappingTarget JobPost entity);

    @Override
    JobPostVO entityToVo(JobPost entity);
}
