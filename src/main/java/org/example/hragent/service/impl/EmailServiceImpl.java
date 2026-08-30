package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务实现：基于 QQ 邮箱 SMTP（spring-boot-starter-mail）。
 * <p>
 * 发件人固定为 spring.mail.username 配置的 QQ 邮箱。
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${hragent.mail.default-to:2670556804@qq.com}")
    private String defaultTo;

    @Autowired
    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendSimpleMail(String to, String subject, String content) {
        String target = (to == null || to.isBlank() || !to.contains("@")) ? defaultTo : to;
        log.info("发送邮件 to={}, subject={}, from={}", target, subject, from);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(target);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("邮件发送成功 to={}", target);
        } catch (Exception e) {
            log.error("邮件发送失败 to={}, subject={}, err={}", target, subject, e.getMessage(), e);
        }
    }
}
