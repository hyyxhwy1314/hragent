package org.example.hragent.dto;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class AbilityTagUpdateDto {

    /**
     * 标签主键ID
     */
    @NotNull
    private Long id;

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 标签编码，唯一标识
     */
    private String tagCode;

    /**
     * 标签类别（如：技能、性格、综合素质）
     */
    private String tagCategory;

    /**
     * 排序序号
     */
    private Integer sort;

    /**
     * 标签状态：0‑禁用 1‑启用
     */
    private Integer status;
}