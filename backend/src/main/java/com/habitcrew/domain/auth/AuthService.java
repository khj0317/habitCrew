package com.habitcrew.domain.auth;

import com.habitcrew.domain.auth.dto.LoginRequest;
import com.habitcrew.domain.auth.dto.SignupRequest;
import com.habitcrew.domain.auth.dto.TokenResponse;
import com.habitcrew.domain.user.User;
import com.habitcrew.domain.user.UserRepository;
import com.habitcrew.domain.user.UserResponse;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final Clock clock;

    /** 가입되지 않은 이메일로 로그인해도 비밀번호 비교 시간을 똑같이 써서, 응답 시간으로 가입 여부를 알 수 없게 한다 */
    private volatile String dummyHash;

    public record Session(TokenResponse body, String refreshToken) {
    }

    @Transactional
    public Session signup(SignupRequest request) {
        String email = request.email();
        String nickname = request.nickname();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        User user = userRepository.saveAndFlush(
                new User(email, passwordEncoder.encode(request.password()), nickname, clock.instant()));
        return issue(user);
    }

    @Transactional(readOnly = true)
    public Session login(LoginRequest request) {
        String email = request.email();
        loginAttemptLimiter.checkNotLocked(email);

        User user = userRepository.findByEmail(email).orElse(null);
        String hash = user == null ? dummyHash() : user.getPasswordHash();
        if (!passwordEncoder.matches(request.password(), hash) || user == null) {
            loginAttemptLimiter.recordFailure(email);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        loginAttemptLimiter.reset(email);
        return issue(user);
    }

    /** 리프레시 토큰을 소비하고 새 토큰 쌍을 발급한다 (한 번 쓴 토큰은 다시 쓸 수 없다). */
    @Transactional(readOnly = true)
    public Session refresh(String refreshToken) {
        Long userId = refreshTokenStore.consume(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issue(user);
    }

    public void logout(String refreshToken) {
        refreshTokenStore.consume(refreshToken);
    }

    private Session issue(User user) {
        String accessToken = accessTokenIssuer.issue(user.getId());
        String refreshToken = refreshTokenStore.issue(user.getId());
        TokenResponse body = new TokenResponse(accessToken, accessTokenIssuer.ttlSeconds(), UserResponse.from(user));
        return new Session(body, refreshToken);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-password-for-timing");
        }
        return dummyHash;
    }
}
