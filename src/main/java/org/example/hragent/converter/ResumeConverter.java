package org.example.hragent.converter;

import org.example.hragent.dto.ResumeSaveDto;
import org.example.hragent.dto.ResumeUpdateDto;
import org.example.hragent.entity.Resume;
import org.example.hragent.vo.ResumeVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ResumeConverter extends BaseFullConverter<Resume, ResumeVO, ResumeSaveDto, ResumeUpdateDto> {

    @Override
    Resume saveDtoToEntity(ResumeSaveDto dto);

    @Override
    void updateDtoToEntity(ResumeUpdateDto dto, @MappingTarget Resume entity);

    @Override
    ResumeVO entityToVo(Resume entity);
}
