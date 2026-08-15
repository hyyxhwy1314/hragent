package org.example.hragent.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 实体基类，所有数据库实体继承该类
 */
@Data
public class BaseEntity {

    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间，新增自动填充
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy‑MM‑dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间，新增&修改自动填充
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy‑MM‑dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * 删除时间（逻辑删除时间戳）
     */
    @TableField("delete_time")
    @JsonFormat(pattern = "yyyy‑MM‑dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime deleteTime;

    /**
     * 逻辑删除标识：0‑未删除，1‑已删除
     */
    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}