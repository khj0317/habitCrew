package com.habitcrew.domain.guest;

import com.habitcrew.domain.crew.Crew;
import com.habitcrew.domain.crew.CrewMember;
import com.habitcrew.domain.crew.CrewMemberRepository;
import com.habitcrew.domain.crew.CrewRepository;
import com.habitcrew.domain.crew.InviteCodeGenerator;
import com.habitcrew.domain.notification.NotificationService;
import com.habitcrew.domain.settlement.SettlementService;
import com.habitcrew.domain.user.User;
import com.habitcrew.domain.user.UserRepository;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.global.time.ServiceTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * "로그인 없이 둘러보기": 방문자마다 따로 쓰는 체험 계정과, 2주치 기록이 채워진 체험 크루를 만든다.
 *
 * <ul>
 *   <li>모두가 쓰는 데모 계정 하나를 두면 누군가 크루를 나가거나 이름을 바꿔 다음 사람이 망가진 화면을 본다.
 *       그래서 방문자마다 자기만의 모래상자를 준다.</li>
 *   <li>크루원 3명(가상 인물)과 14일치 인증·좋아요·댓글을 넣고, 실제 정산 로직으로 지난 날짜를 정산해
 *       랭킹·달력·연속 인증·벌금이 처음부터 다 보이게 한다. 오늘 인증은 비워 두어 직접 올려 볼 수 있다.</li>
 *   <li>24시간 뒤 정리 작업이 통째로 지운다. 같은 IP에서 1시간에 10개까지만(설정 가능) 만들 수 있다 (남용 방지).</li>
 * </ul>
 */
@Service
public class GuestService {

    static final Duration LIFETIME = Duration.ofHours(24);
    static final int HISTORY_DAYS = 14;
    private static final String GUEST_DOMAIN = "@guest.habitcrew.local";

    private static final List<String> MESSAGES = List.of(
            "오늘도 해냈다!", "졸렸지만 참고 완료 💪", "어제보다 조금 더", "출근 전에 끝!", "주말에도 쉬지 않기",
            "오늘은 좀 짧게", "비 와도 했어요 ☔", "연속 기록 이어가는 중", "생각보다 재밌네요", "내일도 이 시간에!");

    /** 가상 크루원: 이름, 빼먹은 날(며칠 전), 오늘 인증 여부 */
    private record Bot(String name, Set<Integer> missedDaysAgo, boolean certifiedToday) {
    }

    private static final List<Bot> BOTS = List.of(
            new Bot("책벌레", Set.of(), true),                               // 하루도 안 빠짐
            new Bot("새벽러너", Set.of(11, 7, 3), true),
            new Bot("작심삼일", Set.of(10, 9, 8, 7, 5, 4, 3, 1), false));    // 꾸준하지 않은 친구
    /** 체험 계정 본인: 9일 전·5일 전을 빼먹어 벌금이 있고, 최근 4일 연속 인증 중. 오늘은 직접 올려 볼 수 있게 비워 둔다 */
    private static final Set<Integer> GUEST_MISSED = Set.of(9, 5);

    private final UserRepository userRepository;
    private final CrewRepository crewRepository;
    private final CrewMemberRepository memberRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final SettlementService settlementService;
    private final NotificationService notificationService;
    private final SamplePhotos samplePhotos;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final ServiceTime serviceTime;
    private final TransactionTemplate transaction;
    private final boolean limitEnabled;
    private final int maxPerIpPerHour;
    private final SecureRandom random = new SecureRandom();

    public GuestService(UserRepository userRepository, CrewRepository crewRepository,
                        CrewMemberRepository memberRepository, InviteCodeGenerator inviteCodeGenerator,
                        SettlementService settlementService, NotificationService notificationService,
                        SamplePhotos samplePhotos,
                        PasswordEncoder passwordEncoder, StringRedisTemplate redis, JdbcTemplate jdbc,
                        ServiceTime serviceTime, PlatformTransactionManager transactionManager,
                        @Value("${habitcrew.guest.rate-limit-enabled:true}") boolean limitEnabled,
                        @Value("${habitcrew.guest.max-per-ip-per-hour:10}") int maxPerIpPerHour) {
        this.maxPerIpPerHour = maxPerIpPerHour;
        this.userRepository = userRepository;
        this.crewRepository = crewRepository;
        this.memberRepository = memberRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.settlementService = settlementService;
        this.notificationService = notificationService;
        this.samplePhotos = samplePhotos;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.jdbc = jdbc;
        this.serviceTime = serviceTime;
        this.transaction = new TransactionTemplate(transactionManager);
        this.limitEnabled = limitEnabled;
    }

    public User create(String clientIp) {
        checkRateLimit(clientIp);
        List<SamplePhotos.Photo> photos = samplePhotos.photos();
        LocalDate today = serviceTime.today();
        Instant now = serviceTime.now();
        Instant expiresAt = now.plus(LIFETIME);
        // 크루와 크루원은 2주 전 아침 9시에 시작한 것으로 한다 (그래야 지난 날짜가 정산 대상이 된다)
        Instant start = today.minusDays(HISTORY_DAYS).atTime(LocalTime.of(9, 0)).atZone(serviceTime.zone()).toInstant();
        // 비밀번호는 아무도 모르는 무작위 값 (체험 계정은 비밀번호로 로그인할 수 없다)
        String passwordHash = passwordEncoder.encode(randomToken(24));

        Long[] ids = transaction.execute(status -> {
            User guest = userRepository.save(User.guest(email(), passwordHash, "체험러_" + randomToken(3), now, expiresAt));
            List<User> bots = new ArrayList<>();
            for (Bot bot : BOTS) {
                bots.add(userRepository.save(User.guest(email(), passwordHash, bot.name() + "_" + randomToken(3), now, expiresAt)));
            }

            Crew crew = crewRepository.save(new Crew("하루 한 페이지 독서 (체험)",
                    "체험용 크루예요. 매일 인증하고, 빼먹으면 1,000P 벌금이 쌓여요. 오늘 인증을 직접 올려 보세요!",
                    EnumSet.allOf(DayOfWeek.class), 1000, 10, inviteCodeGenerator.generate(), guest.getId(), start));
            memberRepository.save(new CrewMember(crew, guest, CrewMember.Role.OWNER, start));
            for (User bot : bots) {
                memberRepository.save(new CrewMember(crew, bot, CrewMember.Role.MEMBER, start.plusSeconds(60)));
            }

            List<Long> guestCertIds = new ArrayList<>();
            for (int daysAgo = HISTORY_DAYS - 1; daysAgo >= 0; daysAgo--) {
                LocalDate date = today.minusDays(daysAgo);
                if (daysAgo > 0 && !GUEST_MISSED.contains(daysAgo)) {
                    guestCertIds.add(insertCertification(crew.getId(), guest.getId(), date, 7, photos, daysAgo));
                }
                for (int i = 0; i < BOTS.size(); i++) {
                    Bot bot = BOTS.get(i);
                    boolean certifies = daysAgo == 0 ? bot.certifiedToday() : !bot.missedDaysAgo().contains(daysAgo);
                    if (certifies) {
                        insertCertification(crew.getId(), bots.get(i).getId(), date, 6 + i * 3, photos, daysAgo + i + 1);
                    }
                }
            }
            Long reacted = addReactions(guestCertIds, bots);
            return new Long[]{guest.getId(), crew.getId(), reacted};
        });

        // 지난 날짜는 실제 정산 로직으로 정산한다 (벌금·정산 내역이 진짜 규칙대로 쌓인다)
        for (int daysAgo = HISTORY_DAYS - 1; daysAgo >= 1; daysAgo--) {
            settlementService.settle(ids[1], today.minusDays(daysAgo));
        }
        addSampleNotifications(ids[0], ids[1], ids[2]);
        return userRepository.findById(ids[0]).orElseThrow();
    }

    private Long insertCertification(Long crewId, Long userId, LocalDate date, int hour, List<SamplePhotos.Photo> photos,
                                     int seed) {
        SamplePhotos.Photo photo = photos.get(Math.floorMod(seed, photos.size()));
        Instant createdAt = date.atTime(hour, (seed * 7) % 60).atZone(serviceTime.zone()).toInstant();
        String message = seed % 3 == 0 ? null : MESSAGES.get(Math.floorMod(seed, MESSAGES.size()));
        return jdbc.queryForObject("""
                        insert into certifications (crew_id, user_id, cert_date, message, image_key, thumbnail_key,
                                                    image_width, image_height, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                        """, Long.class,
                crewId, userId, Date.valueOf(date), message, photo.imageKey(), photo.thumbnailKey(),
                photo.width(), photo.height(), Timestamp.from(createdAt));
    }

    /**
     * 체험 계정의 최근 인증에 크루원들이 좋아요·응원 댓글을 남겨 둔다
     *
     * @return 댓글을 남긴 인증 id (없으면 null)
     */
    private Long addReactions(List<Long> guestCertIds, List<User> bots) {
        List<Long> recent = guestCertIds.subList(Math.max(0, guestCertIds.size() - 3), guestCertIds.size());
        for (Long certId : recent) {
            for (User bot : bots.subList(0, 2)) {
                jdbc.update("insert into certification_likes (certification_id, user_id) values (?, ?)", certId, bot.getId());
            }
            jdbc.update("update certifications set like_count = like_count + 2 where id = ?", certId);
        }
        if (!recent.isEmpty()) {
            Long latest = recent.getLast();
            String[][] comments = {{"0", "벌써 4일 연속이네요! 👏"}, {"1", "저도 내일 같이 가요 🔥"}};
            for (String[] c : comments) {
                jdbc.update("insert into certification_comments (certification_id, user_id, content, created_at) values (?, ?, ?, ?)",
                        latest, bots.get(Integer.parseInt(c[0])).getId(), c[1], Timestamp.from(serviceTime.now()));
            }
            jdbc.update("update certifications set comment_count = comment_count + ? where id = ?", comments.length, latest);
            return latest;
        }
        return null;
    }

    /**
     * 알림함도 비어 있지 않게: 위에서 남긴 좋아요·댓글을 알림으로 넣어 둔다.
     * 벌금 알림은 정산이 실제 규칙대로 남기므로, 그보다 뒤에(= 목록 위쪽에) 넣는다
     */
    private void addSampleNotifications(Long guestId, Long crewId, Long certId) {
        if (certId == null) {
            return;
        }
        String url = "/crews/" + crewId + "/certifications/" + certId;
        List<String[]> comments = jdbc.query("""
                        select u.nickname, cm.content from certification_comments cm join users u on u.id = cm.user_id
                        where cm.certification_id = ? order by cm.id
                        """,
                (rs, i) -> new String[]{rs.getString("nickname"), rs.getString("content")}, certId);
        if (comments.isEmpty()) {
            return;
        }
        notificationService.create(guestId, NotificationService.Type.LIKE, "좋아요를 받았어요",
                comments.getFirst()[0] + "님 외 1명이 내 인증을 좋아해요", url);
        for (String[] c : comments) {
            notificationService.create(guestId, NotificationService.Type.COMMENT, "새 댓글", c[0] + ": " + c[1], url);
        }
    }

    private void checkRateLimit(String clientIp) {
        if (!limitEnabled) {
            return;
        }
        String key = "guest:create:" + clientIp;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofHours(1));
        }
        if (count != null && count > maxPerIpPerHour) {
            throw new BusinessException(ErrorCode.GUEST_LIMIT);
        }
    }

    private String email() {
        return "guest-" + randomToken(9) + GUEST_DOMAIN;
    }

    private String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b).replace('-', 'x').replace('_', 'z');
    }
}
