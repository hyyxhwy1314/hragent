package org.example.hragent.vo;

import lombok.Data;

@Data
public class TrainingCourseVO {
    private Long id;
    private String courseName;
    private String courseCode;
    private String courseType;
    private String courseDesc;
    private String courseTarget;
    private Integer durationMin;
    private String tagIds;
    private Integer status;
}