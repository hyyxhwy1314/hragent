package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.TrainingCourseSaveDto;
import org.example.hragent.dto.TrainingCourseUpdateDto;
import org.example.hragent.entity.TTrainingCourse;
import org.example.hragent.vo.TrainingCourseVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-14T10:43:47+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class TrainingCourseConverterImpl implements TrainingCourseConverter {

    @Override
    public TTrainingCourse saveDtoToEntity(TrainingCourseSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        TTrainingCourse tTrainingCourse = new TTrainingCourse();

        tTrainingCourse.setCourseName( dto.getCourseName() );
        tTrainingCourse.setCourseCode( dto.getCourseCode() );
        tTrainingCourse.setCourseType( dto.getCourseType() );
        tTrainingCourse.setCourseDesc( dto.getCourseDesc() );
        tTrainingCourse.setCourseTarget( dto.getCourseTarget() );
        tTrainingCourse.setDurationMin( dto.getDurationMin() );
        tTrainingCourse.setTagIds( dto.getTagIds() );
        tTrainingCourse.setStatus( dto.getStatus() );

        return tTrainingCourse;
    }

    @Override
    public void updateDtoToEntity(TrainingCourseUpdateDto dto, TTrainingCourse entity) {
        if ( dto == null ) {
            return;
        }

        entity.setId( dto.getId() );
        entity.setCourseName( dto.getCourseName() );
        entity.setCourseCode( dto.getCourseCode() );
        entity.setCourseType( dto.getCourseType() );
        entity.setCourseDesc( dto.getCourseDesc() );
        entity.setCourseTarget( dto.getCourseTarget() );
        entity.setDurationMin( dto.getDurationMin() );
        entity.setTagIds( dto.getTagIds() );
        entity.setStatus( dto.getStatus() );
    }

    @Override
    public TrainingCourseVO entityToVo(TTrainingCourse entity) {
        if ( entity == null ) {
            return null;
        }

        TrainingCourseVO trainingCourseVO = new TrainingCourseVO();

        trainingCourseVO.setId( entity.getId() );
        trainingCourseVO.setCourseName( entity.getCourseName() );
        trainingCourseVO.setCourseCode( entity.getCourseCode() );
        trainingCourseVO.setCourseType( entity.getCourseType() );
        trainingCourseVO.setCourseDesc( entity.getCourseDesc() );
        trainingCourseVO.setCourseTarget( entity.getCourseTarget() );
        trainingCourseVO.setDurationMin( entity.getDurationMin() );
        trainingCourseVO.setTagIds( entity.getTagIds() );
        trainingCourseVO.setStatus( entity.getStatus() );

        return trainingCourseVO;
    }
}
