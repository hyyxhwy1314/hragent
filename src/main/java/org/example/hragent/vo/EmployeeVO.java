package org.example.hragent.vo;
import lombok.Data;
import java.time.LocalDate;

/**
 * 员工信息返回视图对象
 */
@Data
public class EmployeeVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 员工工号
     */
    private String empNo;

    /**
     * 员工姓名
     */
    private String empName;

    /**
     * 性别：0‑女，1‑男
     */
    private Integer gender;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 岗位名称
     */
    private String positionName;

    /**
     * 入职日期
     */
    private LocalDate entryDate;

    /**
     * 转正日期
     */
    private LocalDate regularDate;

    /**
     * 离职日期
     */
    private LocalDate leaveDate;

    /**
     * 员工状态：0‑在职，1‑离职
     */
    private Integer empStatus;

    /**
     * 工作城市
     */
    private String workCity;

    /**
     * 备注
     */
    private String remark;
}