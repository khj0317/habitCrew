package com.habitcrew.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        // 대부분의 메일 서버는 한글 등 ASCII가 아닌 주소를 받지 않아, 비밀번호 찾기 메일을 보낼 수 없게 된다
        @Pattern(regexp = "^[!-~]+$", message = "이메일은 영문·숫자·기호로 입력해 주세요.")
        @Size(max = 255)
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자로 입력해 주세요.")
        @Pattern(regexp = "^[가-힣A-Za-z0-9_]+$", message = "닉네임은 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요.")
        String nickname
) {
    /** 복사해 붙여넣은 이메일·닉네임의 앞뒤 공백과 이메일 대소문자를 검증 전에 정리한다 */
    public SignupRequest {
        email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
        nickname = nickname == null ? null : nickname.strip();
    }
}
