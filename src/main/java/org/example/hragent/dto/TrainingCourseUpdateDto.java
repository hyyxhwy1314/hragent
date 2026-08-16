package org.example.hragent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingCourseUpdateDto {

    @NotNull(message = "id不能为空")
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
