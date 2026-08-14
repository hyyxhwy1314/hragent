package org.example.hragent.converter;

import org.example.hragent.dto.TrainingCourseSaveDto;
import org.example.hragent.dto.TrainingCourseUpdateDto;
import org.example.hragent.entity.TTrainingCourse;
import org.example.hragent.vo.TrainingCourseVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface TrainingCourseConverter {

    TTrainingCourse saveDtoToEntity(TrainingCourseSaveDto dto);

    void updateDtoToEntity(TrainingCourseUpdateDto dto, @MappingTarget TTrainingCourse entity);

    TrainingCourseVO entityToVo(TTrainingCourse entity);

    default List<TrainingCourseVO> entityListToVoList(List<TTrainingCourse> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}