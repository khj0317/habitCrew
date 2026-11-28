package com.habitcrew.global.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP로 메일을 보낸다 (로컬은 Mailpit, 배포 시 실제 메일 서버) */
@Slf4j
@Component
public class SmtpMailer implements Mailer {

    private final JavaMailSender sender;
    private final String from;

    public SmtpMailer(JavaMailSender sender, @Value("${habitcrew.mail-from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (RuntimeException e) {
            // 메일은 따로(비동기로) 보내므로 실패해도 사용자 요청에는 영향이 없다. 원인만 남긴다
            log.warn("메일을 보내지 못했습니다: to={}, 이유={}", to, e.getMessage());
        }
    }
}
