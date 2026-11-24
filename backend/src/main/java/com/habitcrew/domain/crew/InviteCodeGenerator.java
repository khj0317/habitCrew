package com.habitcrew.domain.crew;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 8자리 초대 코드를 만든다.
 * - 0/O, 1/I/L처럼 헷갈리는 글자는 빼서 말로 불러 주거나 손으로 옮겨 적어도 틀리지 않게 했다
 * - 31^8 ≈ 8,500억 가지라서 아무 코드나 넣어 남의 크루에 들어가는 것은 사실상 불가능하다
 * - 예측할 수 없도록 SecureRandom을 쓴다
 */
@Component
public class InviteCodeGenerator {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
