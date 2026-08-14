package org.example.hragent.converter;

import org.example.hragent.dto.TrainingCourseSaveDto;
import org.example.hragent.dto.TrainingCourseUpdateDto;
import org.example.hragent.entity.TrainingCourse;
import org.example.hragent.entity.TrainingCourse;
import org.example.hragent.vo.TrainingCourseVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface TrainingCourseConverter {

    TrainingCourse saveDtoToEntity(TrainingCourseSaveDto dto);

    void updateDtoToEntity(TrainingCourseUpdateDto dto, @MappingTarget TrainingCourse entity);

    TrainingCourseVO entityToVo(TrainingCourse entity);

    default List<TrainingCourseVO> entityListToVoList(List<TrainingCourse> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}