package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField("delete_time")
    private LocalDateTime deleteTime;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
