package org.example.hragent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.Employee;
import org.example.hragent.mapper.EmployeeMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时数据初始化
 * <p>
 * 给 password 为空的员工设置默认密码 123456 的 BCrypt 哈希，
 * 避免硬编码不确定的哈希值到 SQL 中。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final EmployeeMapper employeeMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 查询所有 password 为空的员工
        List<Employee> noPasswordEmployees = employeeMapper.selectList(
                new LambdaQueryWrapper<Employee>()
                        .isNull(Employee::getPassword)
                        .or()
                        .eq(Employee::getPassword, "")
        );

        if (noPasswordEmployees.isEmpty()) {
            return;
        }

        String defaultPasswordHash = passwordEncoder.encode("123456");
        for (Employee emp : noPasswordEmployees) {
            employeeMapper.update(null,
                    new LambdaUpdateWrapper<Employee>()
                            .eq(Employee::getId, emp.getId())
                            .set(Employee::getPassword, defaultPasswordHash)
            );
        }
        log.info("已为 {} 名员工设置默认密码 123456", noPasswordEmployees.size());
    }
}
