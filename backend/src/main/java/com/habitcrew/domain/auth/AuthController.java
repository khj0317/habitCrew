package com.habitcrew.domain.auth;

import com.habitcrew.domain.auth.dto.LoginRequest;
import com.habitcrew.domain.auth.dto.SignupRequest;
import com.habitcrew.domain.auth.dto.TokenResponse;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signup(@Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        return respond(authService.signup(request), response);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return respond(authService.login(request), response);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = refreshTokenCookie.read(request)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        try {
            return respond(authService.refresh(token), response);
        } catch (BusinessException e) {
            refreshTokenCookie.clear(response); // 쓸 수 없는 쿠키는 지워서 매번 재시도하지 않게 한다
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        refreshTokenCookie.read(request).ifPresent(authService::logout);
        refreshTokenCookie.clear(response);
    }

    private TokenResponse respond(AuthService.Session session, HttpServletResponse response) {
        refreshTokenCookie.write(response, session.refreshToken());
        return session.body();
    }
}
