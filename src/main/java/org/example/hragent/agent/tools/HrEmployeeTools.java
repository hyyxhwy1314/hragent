package org.example.hragent.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.JobPost;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.service.JobPostService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 员工与岗位查询工具。
 */
@Component
public class HrEmployeeTools implements HrToolSet {

    private final EmployeeService employeeService;
    private final JobPostService jobPostService;
    private final HrToolSupport support;

    public HrEmployeeTools(EmployeeService employeeService,
                           JobPostService jobPostService,
                           HrToolSupport support) {
        this.employeeService = employeeService;
        this.jobPostService = jobPostService;
        this.support = support;
    }

    /** 查询指定员工的详细信息 */
    @Tool("查询指定员工的详细信息，参数为员工姓名或员工ID")
    public String queryEmployeeInfo(String query) {
        try {
            String searchTerm = support.extractSearchTerm(query);
            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供要查询的员工姓名或ID";
            }

            // 先按 ID 查询，找不到再按姓名查询
            Employee employee = null;
            try {
                employee = employeeService.getById(Long.parseLong(searchTerm));
            } catch (NumberFormatException e) {
                LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
                wrapper.like(Employee::getEmpName, searchTerm);
                List<Employee> employees = employeeService.list(wrapper);
                if (!employees.isEmpty()) {
                    employee = employees.get(0);
                }
            }

            if (employee == null) {
                return String.format("未找到匹配的员工：%s", searchTerm);
            }
            return support.formatEmployeeInfo(employee);
        } catch (Exception e) {
            return "查询员工信息出错：" + e.getMessage();
        }
    }

    /** 查询系统中所有员工列表 */
    @Tool("查询系统中所有员工的列表")
    public String queryEmployeeList() {
        try {
            List<Employee> employees = employeeService.list();
            if (employees.isEmpty()) {
                return "系统中未找到任何员工";
            }
            StringBuilder result = new StringBuilder();
            result.append("共找到 ").append(employees.size()).append(" 名员工：\n");
            for (Employee employee : employees) {
                result.append(String.format("- ID: %d, 姓名: %s, 部门: %s, 岗位: %s\n",
                        employee.getId(), employee.getEmpName(), employee.getDeptName(), employee.getPositionName()));
            }
            return result.toString();
        } catch (Exception e) {
            return "查询员工列表出错：" + e.getMessage();
        }
    }

    /** 查询指定岗位的详细信息 */
    @Tool("查询指定岗位的详细信息，参数为岗位名称或岗位ID")
    public String queryJobInfo(String query) {
        try {
            String searchTerm = support.extractSearchTerm(query);
            if (searchTerm == null || searchTerm.isEmpty()) {
                return "请提供要查询的岗位名称或ID";
            }

            JobPost jobPost = null;
            try {
                jobPost = jobPostService.getById(Long.parseLong(searchTerm));
            } catch (NumberFormatException e) {
                LambdaQueryWrapper<JobPost> wrapper = new LambdaQueryWrapper<>();
                wrapper.like(JobPost::getJobName, searchTerm);
                List<JobPost> jobPosts = jobPostService.list(wrapper);
                if (!jobPosts.isEmpty()) {
                    jobPost = jobPosts.get(0);
                }
            }

            if (jobPost == null) {
                return String.format("未找到匹配的岗位：%s", searchTerm);
            }
            return support.formatJobInfo(jobPost);
        } catch (Exception e) {
            return "查询岗位信息出错：" + e.getMessage();
        }
    }

    /** 查询系统中所有岗位列表 */
    @Tool("查询系统中所有岗位的列表")
    public String queryJobList() {
        try {
            List<JobPost> jobPosts = jobPostService.list();
            if (jobPosts.isEmpty()) {
                return "系统中未找到任何岗位";
            }
            StringBuilder result = new StringBuilder();
            result.append("共找到 ").append(jobPosts.size()).append(" 个岗位：\n");
            for (JobPost jobPost : jobPosts) {
                result.append(String.format("- ID: %d, 岗位名称: %s, 所属部门: %s, 状态: %d\n",
                        jobPost.getId(), jobPost.getJobName(), jobPost.getDeptName(), jobPost.getJobStatus()));
            }
            return result.toString();
        } catch (Exception e) {
            return "查询岗位列表出错：" + e.getMessage();
        }
    }
}