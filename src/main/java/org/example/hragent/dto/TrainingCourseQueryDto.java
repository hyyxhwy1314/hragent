package org.example.hragent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TrainingCourseQueryDto extends BaseQueryDto {

    private String courseName;

    private String tagIds;

    private Integer status;
}
