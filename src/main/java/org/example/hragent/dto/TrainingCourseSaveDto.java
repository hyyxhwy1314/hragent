package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrainingCourseSaveDto {

    @NotBlank(message = "课程名称不能为空")
    private String courseName;

    @NotBlank(message = "课程编码不能为空")
    private String courseCode;

    private String courseType;

    private String courseDesc;

    private String courseTarget;

    private Integer durationMin;

    private String tagIds;

    private Integer status;
}