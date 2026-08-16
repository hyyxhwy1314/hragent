package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingCourseSaveDto {

    /**
     * 课程名称
     */
    @NotBlank(message = "课程名称不能为空")
    private String courseName;

    /**
     * 课程编码，唯一标识
     */
    @NotBlank(message = "课程编码不能为空")
    private String courseCode;

    /**
     * 课程类型：线上/线下等
     */
    private String courseType;

    /**
     * 课程详细描述
     */
    private String courseDesc;

    /**
     * 课程学习目标
     */
    private String courseTarget;

    /**
     * 课程时长(分钟)
     */
    private Integer durationMin;

    /**
     * 关联能力标签ID，多个逗号分隔
     */
    private String tagIds;

    /**
     * 课程状态：0-禁用 1-启用
     */
    private Integer status;
}
