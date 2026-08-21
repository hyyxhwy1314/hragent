package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传返回VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileVO {

    /** 文件记录ID（t_sys_file.id） */
    private Long id;

    /** 对象存储 key */
    private String objectKey;

    /** 原始文件名 */
    private String originalName;

    /** 文件类型 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 存储类型 */
    private String storageType;

    /** 预签名预览URL */
    private String previewUrl;
}
