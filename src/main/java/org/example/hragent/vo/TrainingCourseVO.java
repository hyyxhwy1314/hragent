package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
