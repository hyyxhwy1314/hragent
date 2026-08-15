package org.example.hragent.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_ability_tag")
public class AbilityTag extends BaseEntity {

    /**
     * 标签名称
     */
    @TableField("tag_name")
    private String tagName;

    /**
     * 标签编码
     */
    @TableField("tag_code")
    private String tagCode;

    /**
     * 标签分类
     */
    @TableField("tag_category")
    private String tagCategory;

    /**
     * 排序
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 状态 0禁用 1启用
     */
    @TableField("status")
    private Integer status;
}