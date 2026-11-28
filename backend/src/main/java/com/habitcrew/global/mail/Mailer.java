package com.habitcrew.global.mail;

/** 메일 보내기. 테스트에서는 실제로 보내지 않고 기록만 하는 구현으로 바꿔 끼운다 */
public interface Mailer {

    void send(String to, String subject, String text);
}
