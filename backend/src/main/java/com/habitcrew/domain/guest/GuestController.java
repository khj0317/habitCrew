package com.habitcrew.domain.guest;

import com.habitcrew.domain.auth.AuthService;
import com.habitcrew.domain.auth.RefreshTokenCookie;
import com.habitcrew.domain.auth.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;
    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    /** 로그인 없이 둘러보기: 체험 계정을 만들고 바로 로그인 상태로 만든다 */
    @PostMapping("/api/auth/guest")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse create(HttpServletRequest request, HttpServletResponse response) {
        AuthService.Session session = authService.issue(guestService.create(request.getRemoteAddr()));
        refreshTokenCookie.write(response, session.refreshToken());
        return session.body();
    }
}
