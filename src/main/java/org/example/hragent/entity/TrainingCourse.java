package org.example.hragent.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_training_course")
public class TrainingCourse extends BaseEntity {

    /**
     * 课程名称
     */
    @TableField("course_name")
    private String courseName;

    /**
     * 课程编码
     */
    @TableField("course_code")
    private String courseCode;

    /**
     * 课程类型
     */
    @TableField("course_type")
    private String courseType;

    /**
     * 课程描述
     */
    @TableField("course_desc")
    private String courseDesc;

    /**
     * 学习目标
     */
    @TableField("course_target")
    private String courseTarget;

    /**
     * 课程时长（分钟）
     */
    @TableField("duration_min")
    private Integer durationMin;

    /**
     * 关联标签id，逗号分隔
     */
    @TableField("tag_ids")
    private String tagIds;

    /**
     * 状态 0下架 1上架
     */
    @TableField("status")
    private Integer status;
}