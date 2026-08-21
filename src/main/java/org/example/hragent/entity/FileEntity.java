package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统文件实体
 * 记录上传到对象存储的文件元信息，供简历、JD 等业务关联引用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_sys_file")
public class FileEntity extends BaseEntity {

    /**
     * 对象存储 key
     */
    @TableField("object_key")
    private String objectKey;

    /**
     * 原始文件名
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 文件类型（MIME 或扩展名）
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 存储类型，如 COS，预留扩展
     */
    @TableField("storage_type")
    private String storageType;
}
