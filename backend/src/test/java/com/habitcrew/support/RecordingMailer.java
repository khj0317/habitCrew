package com.habitcrew.support;

import com.habitcrew.global.mail.Mailer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** 테스트용: 메일을 보내지 않고 기록만 한다 */
public class RecordingMailer implements Mailer {

    public record Mail(String to, String subject, String text) {
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String text) {
        sent.add(new Mail(to, subject, text));
    }

    public Optional<Mail> lastTo(String to) {
        return sent.stream().filter(m -> m.to().equals(to)).reduce((first, second) -> second);
    }

    public long countTo(String to) {
        return sent.stream().filter(m -> m.to().equals(to)).count();
    }
}
