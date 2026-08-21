package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.converter.TrainingCourseConverter;
import org.example.hragent.dto.TrainingCourseQueryDto;
import org.example.hragent.dto.TrainingCourseSaveDto;
import org.example.hragent.dto.TrainingCourseUpdateDto;
import org.example.hragent.entity.TrainingCourse;
import org.example.hragent.service.TrainingCourseService;
import org.example.hragent.vo.TrainingCourseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/training-courses")
public class TrainingCourseController extends BaseCrudController<TrainingCourse, TrainingCourseVO, TrainingCourseQueryDto, TrainingCourseSaveDto, TrainingCourseUpdateDto> {

    @Autowired
    private TrainingCourseService trainingCourseService;

    @Autowired
    private TrainingCourseConverter trainingCourseConverter;

    @Override
    protected TrainingCourseService baseService() {
        return trainingCourseService;
    }

    @Override
    protected TrainingCourseConverter baseConverter() {
        return trainingCourseConverter;
    }

    @Override
    protected TrainingCourseConverter fullConverter() {
        return trainingCourseConverter;
    }

    @Override
    protected LambdaQueryWrapper<TrainingCourse> buildWrapper(TrainingCourseQueryDto queryDto) {
        LambdaQueryWrapper<TrainingCourse> w = new LambdaQueryWrapper<>();
        w.like(queryDto.getCourseName() != null && !queryDto.getCourseName().isBlank(), TrainingCourse::getCourseName, queryDto.getCourseName())
         .eq(queryDto.getStatus() != null, TrainingCourse::getStatus, queryDto.getStatus());
        return w;
    }
}
