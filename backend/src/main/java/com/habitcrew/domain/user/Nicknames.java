package com.habitcrew.domain.user;

import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;

import java.util.List;

/** 체험 계정·탈퇴 계정이 쓰는 이름은 일반 회원이 쓸 수 없게 막는다 (사칭 방지) */
public final class Nicknames {

    private static final List<String> RESERVED_PREFIXES = List.of("체험러", "탈퇴한사용자");

    private Nicknames() {
    }

    public static void checkNotReserved(String nickname) {
        if (RESERVED_PREFIXES.stream().anyMatch(nickname::startsWith)) {
            throw new BusinessException(ErrorCode.RESERVED_NICKNAME);
        }
    }
}
