package org.example.hragent.dto;

import lombok.Data;

@Data
public class TrainingCourseQueryDto {
    private String courseName;
    private String tagIds;
    private Integer status;
    private Long pageNum;
    private Long pageSize;
}