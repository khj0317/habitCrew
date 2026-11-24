package com.habitcrew.domain.crew.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.util.Locale;
import java.util.Set;

public final class CrewRequests {

    private CrewRequests() {
    }

    public record Create(
            @NotBlank(message = "크루 이름을 입력해 주세요.")
            @Size(min = 2, max = 30, message = "크루 이름은 2~30자로 입력해 주세요.")
            String name,

            @Size(max = 200, message = "소개는 200자까지 쓸 수 있어요.")
            String description,

            @NotEmpty(message = "인증 요일을 하나 이상 골라 주세요.")
            Set<DayOfWeek> certDays,

            @NotNull(message = "벌금 포인트를 입력해 주세요.")
            @Min(value = 0, message = "벌금 포인트는 0 이상이어야 해요.")
            @Max(value = 10000, message = "벌금 포인트는 10,000까지 정할 수 있어요.")
            Integer penaltyPoints,

            @NotNull(message = "정원을 입력해 주세요.")
            @Min(value = 2, message = "정원은 2명 이상이어야 해요.")
            @Max(value = 30, message = "정원은 30명까지 정할 수 있어요.")
            Integer maxMembers
    ) {
        public Create {
            name = strip(name);
            description = blankToNull(description);
        }
    }

    public record Update(
            @NotBlank(message = "크루 이름을 입력해 주세요.")
            @Size(min = 2, max = 30, message = "크루 이름은 2~30자로 입력해 주세요.")
            String name,

            @Size(max = 200, message = "소개는 200자까지 쓸 수 있어요.")
            String description,

            @NotNull(message = "정원을 입력해 주세요.")
            @Min(value = 2, message = "정원은 2명 이상이어야 해요.")
            @Max(value = 30, message = "정원은 30명까지 정할 수 있어요.")
            Integer maxMembers
    ) {
        public Update {
            name = strip(name);
            description = blankToNull(description);
        }
    }

    public record Join(@NotBlank(message = "초대 코드를 입력해 주세요.") String inviteCode) {
        /** 소문자·공백·하이픈이 섞여도 받아 준다 (예: "abcd-1234 ") */
        public Join {
            inviteCode = normalizeCode(inviteCode);
        }
    }

    public record TransferOwner(@NotNull(message = "새 크루장을 골라 주세요.") Long userId) {
    }

    public static String normalizeCode(String code) {
        return code == null ? null : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
