package org.example.hragent.vo;
import lombok.Data;

/**
 * 培训课程返回视图对象
 */
@Data
public class TrainingCourseVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 课程名称
     */
    private String courseName;

    /**
     * 课程编码
     */
    private String courseCode;

    /**
     * 课程类型
     */
    private String courseType;

    /**
     * 课程简介
     */
    private String courseDesc;

    /**
     * 培训目标
     */
    private String courseTarget;

    /**
     * 课程时长(分钟)
     */
    private Integer durationMin;

    /**
     * 关联能力标签ID集合
     */
    private String tagIds;

    /**
     * 课程状态：0‑禁用，1‑启用
     */
    private Integer status;
}