package org.example.hragent.vo;
import lombok.Data;

/**
 * 能力标签返回视图对象
 */
@Data
public class AbilityTagVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 标签编码
     */
    private String tagCode;

    /**
     * 标签分类
     */
    private String tagCategory;

    /**
     * 排序号
     */
    private Integer sort;

    /**
     * 状态：0‑禁用，1‑启用
     */
    private Integer status;
}