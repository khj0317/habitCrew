package com.habitcrew.domain.user;

import com.habitcrew.domain.auth.AuthService;
import com.habitcrew.domain.auth.RefreshTokenCookie;
import com.habitcrew.domain.auth.dto.TokenResponse;
import com.habitcrew.global.security.LoginUserId;
import com.habitcrew.global.web.RateLimited;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final RefreshTokenCookie refreshTokenCookie;

    /** 비밀번호 찾기: 가입 여부와 상관없이 항상 202로 답한다 */
    @RateLimited(name = "password-reset", limit = 5)
    @PostMapping("/api/auth/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody AccountDtos.ResetRequest request) {
        accountService.requestPasswordReset(request.email());
    }

    @PostMapping("/api/auth/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmReset(@Valid @RequestBody AccountDtos.ResetConfirm request) {
        accountService.resetPassword(request.token(), request.newPassword());
    }

    @PatchMapping("/api/users/me")
    public UserResponse changeNickname(@LoginUserId Long userId, @Valid @RequestBody AccountDtos.ChangeNickname request) {
        return accountService.changeNickname(userId, request.nickname());
    }

    /** 바꾸면 다른 기기는 로그아웃되고, 지금 기기는 새 토큰으로 로그인 상태를 이어 간다 */
    @PutMapping("/api/users/me/password")
    public TokenResponse changePassword(@LoginUserId Long userId, @Valid @RequestBody AccountDtos.ChangePassword request,
                                        HttpServletResponse response) {
        AuthService.Session session = accountService.changePassword(userId, request.currentPassword(), request.newPassword());
        refreshTokenCookie.write(response, session.refreshToken());
        return session.body();
    }

    @PutMapping("/api/users/me/notifications")
    public UserResponse changeNotifications(@LoginUserId Long userId,
                                            @Valid @RequestBody AccountDtos.Notifications request) {
        return accountService.changeNotifications(userId, request.activity());
    }

    @DeleteMapping("/api/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginUserId Long userId, @Valid @RequestBody AccountDtos.DeleteAccount request,
                       HttpServletResponse response) {
        accountService.deleteAccount(userId, request.password());
        refreshTokenCookie.clear(response);
    }
}
