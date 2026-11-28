package com.habitcrew.domain.user;

import com.habitcrew.domain.auth.AuthService;
import com.habitcrew.domain.auth.RefreshTokenStore;
import com.habitcrew.domain.certification.CertificationPhotos;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.global.mail.Mailer;
import com.habitcrew.global.time.ServiceTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 계정 관리: 비밀번호 찾기·바꾸기, 닉네임 바꾸기, 알림 설정, 탈퇴.
 * 체험 계정은 계정 정보를 바꿀 수 없다 (24시간 뒤 통째로 지워진다).
 */
@Slf4j
@Service
public class AccountService {

    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final int RESET_REQUESTS_PER_HOUR = 3;
    private static final String RESET_KEY = "auth:password-reset:";
    private static final String RESET_LIMIT_KEY = "auth:password-reset-limit:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final AuthService authService;
    private final CertificationPhotos certificationPhotos;
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final Mailer mailer;
    private final ServiceTime serviceTime;
    private final String webBaseUrl;
    private final TaskExecutor taskExecutor;
    private final SecureRandom random = new SecureRandom();

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          RefreshTokenStore refreshTokenStore, AuthService authService,
                          CertificationPhotos certificationPhotos, StringRedisTemplate redis, JdbcTemplate jdbc,
                          Mailer mailer, ServiceTime serviceTime,
                          // 스케줄러(taskScheduler)도 TaskExecutor라서 어느 쪽인지 정해 준다
                          @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                          @Value("${habitcrew.web-base-url}") String webBaseUrl) {
        this.taskExecutor = taskExecutor;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
        this.authService = authService;
        this.certificationPhotos = certificationPhotos;
        this.redis = redis;
        this.jdbc = jdbc;
        this.mailer = mailer;
        this.serviceTime = serviceTime;
        this.webBaseUrl = webBaseUrl;
    }

    // ---------------- 비밀번호 찾기 ----------------

    /**
     * 비밀번호 재설정 메일을 보낸다.
     * 가입하지 않은 이메일이어도 똑같이 "보냈어요"라고 답해서, 이 기능으로 가입 여부를 알아낼 수 없게 한다.
     * 링크의 토큰은 30분짜리 1회용이고, Redis에는 원문이 아닌 해시만 둔다.
     */
    public void requestPasswordReset(String rawEmail) {
        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email).filter(u -> !u.isGuest() && !u.isDeleted()).orElse(null);
        if (user == null || !withinResetLimit(email)) {
            return;
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(RESET_KEY + sha256(token), String.valueOf(user.getId()), RESET_TOKEN_TTL);

        // 메일은 따로 보낸다: 메일 서버 응답을 기다리면 가입한 이메일만 응답이 느려져, 응답 시간으로 가입 여부가 드러난다
        String nickname = user.getNickname();
        taskExecutor.execute(() -> mailer.send(email, "[HabitCrew] 비밀번호 재설정 안내", """
                %s님, 안녕하세요.

                아래 링크에서 새 비밀번호를 정해 주세요. 링크는 30분 동안 한 번만 쓸 수 있어요.
                %s/reset-password?token=%s

                비밀번호 찾기를 요청하지 않았다면 이 메일은 무시해 주세요. 지금 비밀번호는 그대로예요.
                """.formatted(nickname, webBaseUrl, token)));
    }

    /** 새 비밀번호로 바꾸고 모든 기기에서 로그아웃시킨다 (누군가 몰래 로그인해 있었을 수도 있으므로) */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String userId = redis.opsForValue().getAndDelete(RESET_KEY + sha256(token));
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
        }
        User user = userRepository.findById(Long.valueOf(userId))
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenStore.revokeAll(user.getId());
    }

    private boolean withinResetLimit(String email) {
        String key = RESET_LIMIT_KEY + email;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofHours(1));
        }
        return count != null && count <= RESET_REQUESTS_PER_HOUR;
    }

    // ---------------- 로그인한 사용자 ----------------

    /** 비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 지금 기기에는 새 세션을 준다 */
    @Transactional
    public AuthService.Session changePassword(Long userId, String currentPassword, String newPassword) {
        User user = editableUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenStore.revokeAll(userId);
        return authService.issue(user);
    }

    @Transactional
    public UserResponse changeNickname(Long userId, String nickname) {
        User user = editableUser(userId);
        Nicknames.checkNotReserved(nickname);
        if (!nickname.equals(user.getNickname()) && userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        user.changeNickname(nickname);
        userRepository.flush(); // 동시에 같은 닉네임으로 바꾸면 유니크 제약이 여기서 막는다
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse changeNotifications(Long userId, boolean activity) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeNotifyActivity(activity);
        return UserResponse.from(user);
    }

    /**
     * 탈퇴.
     * - 크루원이 남아 있는 크루의 크루장이면 먼저 크루장을 넘겨야 한다 (크루가 주인 없이 남지 않게)
     * - 혼자 남은 크루는 지운다
     * - 내 인증 사진·댓글·좋아요는 지우고, 개인정보(이메일·닉네임·비밀번호)를 지운다
     * - 지난 벌금·정산 기록은 크루 기록이라 "탈퇴한사용자"로 남긴다
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = editableUser(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        List<String> activeCrews = jdbc.queryForList("""
                select c.name from crews c
                where c.owner_id = ? and (select count(*) from crew_members m where m.crew_id = c.id) > 1
                """, String.class, userId);
        if (!activeCrews.isEmpty()) {
            throw new BusinessException(ErrorCode.OWNER_OF_ACTIVE_CREWS,
                    "크루원이 남아 있는 크루의 크루장이에요: " + String.join(", ", activeCrews)
                            + ". 크루장을 넘긴 뒤에 탈퇴할 수 있어요.");
        }

        List<String> photoKeys = new ArrayList<>();
        // 1) 혼자 남은 내 크루: 사진을 모아 두고 크루째 지운다 (ON DELETE CASCADE)
        for (Long crewId : jdbc.queryForList("select id from crews where owner_id = ?", Long.class, userId)) {
            photoKeys.addAll(certificationPhotos.keysOfCrew(crewId));
        }
        jdbc.update("delete from crews where owner_id = ?", userId);

        // 2) 다른 크루에 남긴 것: 좋아요·댓글 수를 맞춘 뒤 지운다
        jdbc.update("""
                update certifications c set like_count = like_count - 1
                where exists (select 1 from certification_likes l where l.certification_id = c.id and l.user_id = ?)
                """, userId);
        jdbc.update("delete from certification_likes where user_id = ?", userId);
        jdbc.update("""
                update certifications c set comment_count = comment_count -
                    (select count(*) from certification_comments x where x.certification_id = c.id and x.user_id = ?)
                where exists (select 1 from certification_comments x where x.certification_id = c.id and x.user_id = ?)
                """, userId, userId);
        jdbc.update("delete from certification_comments where user_id = ?", userId);
        jdbc.update("delete from certification_reports where user_id = ?", userId);
        jdbc.query("select image_key, thumbnail_key from certifications where user_id = ?", rs -> {
            photoKeys.add(rs.getString(1));
            photoKeys.add(rs.getString(2));
        }, userId);
        jdbc.update("delete from certifications where user_id = ?", userId);
        jdbc.update("delete from crew_members where user_id = ?", userId);
        jdbc.update("delete from push_subscriptions where user_id = ?", userId);
        jdbc.update("delete from notifications where user_id = ?", userId);

        // 3) 개인정보 지우기 + 모든 기기 로그아웃
        byte[] unusable = new byte[24];
        random.nextBytes(unusable);
        user.anonymize(passwordEncoder.encode(Base64.getEncoder().encodeToString(unusable)), serviceTime.now());
        refreshTokenStore.revokeAll(userId);
        certificationPhotos.deleteAfterCommit(photoKeys);
        log.info("회원 탈퇴: user={}", userId);
    }

    private User editableUser(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isGuest()) {
            throw new BusinessException(ErrorCode.GUEST_ACCOUNT_READONLY);
        }
        return user;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
