package org.example.hragent.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.dto.LoginDto;
import org.example.hragent.entity.Employee;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.utils.JwtUtils;
import org.example.hragent.vo.LoginVO;
import org.example.hragent.vo.R;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 鉴权 Controller
 * <p>
 * 提供登录、获取当前用户、退出登录接口。
 * 登录接口在 {@link org.example.hragent.config.WebMvcConfig} 中放行，不需要 token。
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final EmployeeMapper employeeMapper;
    private final JwtUtils jwtUtils;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(EmployeeMapper employeeMapper, JwtUtils jwtUtils, BCryptPasswordEncoder passwordEncoder) {
        this.employeeMapper = employeeMapper;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登录
     * <p>
     * 支持工号或手机号登录，密码 BCrypt 校验。
     */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDto dto) {
        // 按工号或手机号查询员工
        Employee employee = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getEmpNo, dto.getAccount())
                .or()
                .eq(Employee::getPhone, dto.getAccount())
                .last("LIMIT 1"));

        BusinessException.throwIf(employee == null, ErrorCode.LOGIN_FAIL);
        BusinessException.throwIf(employee.getEmpStatus() != null && employee.getEmpStatus() == 0,
                ErrorCode.ACCOUNT_DISABLED);

        // 密码校验（兼容老数据无密码时用默认密码 123456）
        String dbPassword = employee.getPassword();
        BusinessException.throwIf(dbPassword == null || dbPassword.isBlank()
                || !passwordEncoder.matches(dto.getPassword(), dbPassword), ErrorCode.LOGIN_FAIL);

        // 生成 token
        String token = jwtUtils.generate(employee.getId(), employee.getEmpName(),
                employee.getRole() == null ? "EMPLOYEE" : employee.getRole());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setEmpId(employee.getId());
        vo.setEmpName(employee.getEmpName());
        vo.setRole(employee.getRole());
        log.info("登录成功 empId={}, empName={}, role={}", employee.getId(), employee.getEmpName(), employee.getRole());
        return R.ok(vo);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/me")
    public R<LoginVO> me() {
        CurrentUserService.CurrentUser user = CurrentUserService.get();
        BusinessException.throwIf(user == null, ErrorCode.UNAUTHORIZED);

        LoginVO vo = new LoginVO();
        vo.setEmpId(user.getEmpId());
        vo.setEmpName(user.getEmpName());
        vo.setRole(user.getRole());
        return R.ok(vo);
    }

    /**
     * 退出登录（前端清 token 即可，后端无状态）
     */
    @PostMapping("/logout")
    public R<Boolean> logout() {
        log.info("退出登录 empId={}", CurrentUserService.empId());
        return R.ok(true);
    }
}
