package org.example.hragent.converter;

import org.example.hragent.dto.TrainingCourseSaveDto;
import org.example.hragent.dto.TrainingCourseUpdateDto;
import org.example.hragent.entity.TrainingCourse;
import org.example.hragent.vo.TrainingCourseVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TrainingCourseConverter extends BaseFullConverter<TrainingCourse, TrainingCourseVO, TrainingCourseSaveDto, TrainingCourseUpdateDto> {

    @Override
    TrainingCourse saveDtoToEntity(TrainingCourseSaveDto dto);

    @Override
    void updateDtoToEntity(TrainingCourseUpdateDto dto, @MappingTarget TrainingCourse entity);

    @Override
    TrainingCourseVO entityToVo(TrainingCourse entity);
}
