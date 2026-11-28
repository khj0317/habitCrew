package com.habitcrew.domain.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

    private AccountDtos() {
    }

    private static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).+$";

    public record ResetRequest(@NotBlank(message = "이메일을 입력해 주세요.") String email) {
    }

    public record ResetConfirm(
            @NotBlank String token,
            @NotBlank(message = "새 비밀번호를 입력해 주세요.")
            @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
            @Pattern(regexp = PASSWORD_RULE, message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
            String newPassword
    ) {
    }

    public record ChangePassword(
            @NotBlank(message = "현재 비밀번호를 입력해 주세요.") String currentPassword,
            @NotBlank(message = "새 비밀번호를 입력해 주세요.")
            @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
            @Pattern(regexp = PASSWORD_RULE, message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
            String newPassword
    ) {
    }

    public record ChangeNickname(
            @NotBlank(message = "닉네임을 입력해 주세요.")
            @Size(min = 2, max = 20, message = "닉네임은 2~20자로 입력해 주세요.")
            @Pattern(regexp = "^[가-힣A-Za-z0-9_]+$", message = "닉네임은 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요.")
            String nickname
    ) {
        public ChangeNickname {
            nickname = nickname == null ? null : nickname.strip();
        }
    }

    public record Notifications(@NotNull Boolean activity) {
    }

    public record DeleteAccount(@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
    }
}
