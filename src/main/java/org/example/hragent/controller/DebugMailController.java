package org.example.hragent.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.service.EmailService;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Map;

/**
 * 邮件发送调试接口（临时）
 * <p>
 * 用于验证 QQ 邮箱 SMTP 配置和授权码是否正确，无需走完整入职审批流程。
 */
@Slf4j
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugMailController {

    private final EmailService emailService;

    /**
     * 发送一封测试邮件
     *
     * @param to 收件人邮箱，默认 1151882869@qq.com
     */
    @GetMapping("/mail-test")
    public R<Map<String, Object>> mailTest(@RequestParam(defaultValue = "1151882869@qq.com") String to) {
        String subject = "入职欢迎信 - 测试邮件";
        String content = "尊敬的同事：\n\n" +
                "这是一封来自 HR Agent 系统的测试邮件，用于验证入职通知邮件功能是否正常。\n\n" +
                "如果您收到此邮件，说明 SMTP 配置正确。\n\n" +
                "此致\n人力资源部\n";
        log.info("调试邮件测试开始 to={}", to);
        emailService.sendSimpleMail(to, subject, content);
        return R.ok(Map.of("to", to, "subject", subject, "tip", "请查看日志和收件箱"));
    }
}
