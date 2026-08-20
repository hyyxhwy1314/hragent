package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历解析结果
 * 由简历文件文本提取的结构化字段，用于前端表单自动回填
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParsedData {

    /** 候选人姓名 */
    private String resumeName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 毕业学校 */
    private String school;

    /** 专业 */
    private String major;

    /** 意向岗位 */
    private String expectPosition;

    /** 意向城市 */
    private String expectCity;

    /** 工作年限 */
    private Integer workYears;

    /** 学历 0不限 1大专 2本科 3硕士 4博士 */
    private Integer education;

    /** 简历原始文本（用于回填 resumeContent） */
    private String rawText;
}
