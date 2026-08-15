package org.example.hragent.dto;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingCourseUpdateDto {

    /**
     * 培训课程主键ID
     */
    @NotNull(message = "id不能为空")
    private Long id;

    /**
     * 课程名称
     */
    private String courseName;

    /**
     * 课程编码，唯一标识
     */
    private String courseCode;

    /**
     * 课程类型：线上、线下等
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
     * 课程时长，单位：分钟
     */
    private Integer durationMin;

    /**
     * 关联能力标签ID列表，多个ID逗号分隔
     */
    private String tagIds;

    /**
     * 课程状态：0‑禁用，1‑启用
     */
    private Integer status;
}