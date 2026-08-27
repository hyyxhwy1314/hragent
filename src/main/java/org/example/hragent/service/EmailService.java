package org.example.hragent.service;

/**
 * 邮件发送服务
 * <p>
 * 用于入职流程审批通过后，向员工邮箱发送入职通知。
 */
public interface EmailService {

    /**
     * 发送简单文本邮件
     *
     * @param to      收件人邮箱
     * @param subject  邮件主题
     * @param content 邮件正文（纯文本）
     */
    void sendSimpleMail(String to, String subject, String content);
}