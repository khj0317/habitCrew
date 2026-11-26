package com.habitcrew.domain.push;

import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.global.time.ServiceTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 웹 푸시 구독 관리와 발송.
 *
 * <p><b>SSRF 방지</b>: 구독 주소(endpoint)는 브라우저가 알려 주는 값이라 사용자가 마음대로 바꿔 보낼 수 있다.
 * 아무 주소나 받으면 우리 서버가 그 주소로 요청을 보내게 되므로(내부망 공격 등), https이면서 알려진 푸시 서버 도메인일 때만 받는다.
 */
@Slf4j
@Service
public class PushService {

    private final JdbcTemplate jdbc;
    private final WebPushEncryptor encryptor;
    private final VapidKeys vapidKeys;
    private final PushGateway gateway;
    private final ObjectMapper objectMapper;
    private final ServiceTime serviceTime;
    private final List<String> allowedHosts;

    public PushService(JdbcTemplate jdbc, WebPushEncryptor encryptor, VapidKeys vapidKeys, PushGateway gateway,
                       ObjectMapper objectMapper, ServiceTime serviceTime,
                       @Value("${habitcrew.push.allowed-hosts}") List<String> allowedHosts) {
        this.jdbc = jdbc;
        this.encryptor = encryptor;
        this.vapidKeys = vapidKeys;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.serviceTime = serviceTime;
        this.allowedHosts = allowedHosts.stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
    }

    /** 알림 내용. url은 알림을 눌렀을 때 열 화면, tag가 같으면 휴대폰 알림창에서 이전 알림을 대체한다 */
    public record Message(String title, String body, String url, String tag) {
    }

    public String publicKey() {
        return vapidKeys.publicKey();
    }

    public int subscriptionCount(Long userId) {
        Integer count = jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    /** 같은 기기(endpoint)로 다른 계정이 로그인해 구독하면 그 계정으로 옮긴다 */
    public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
        validate(endpoint, p256dh, auth);
        jdbc.update("""
                        insert into push_subscriptions (user_id, endpoint, p256dh, auth)
                        values (?, ?, ?, ?)
                        on conflict (endpoint) do update
                            set user_id = excluded.user_id, p256dh = excluded.p256dh, auth = excluded.auth
                        """,
                userId, endpoint, p256dh, auth);
    }

    public void unsubscribe(Long userId, String endpoint) {
        jdbc.update("delete from push_subscriptions where user_id = ? and endpoint = ?", userId, endpoint);
    }

    /** 이 사람의 모든 기기에 보낸다. 실제로 접수된 기기 수를 돌려준다 */
    public int sendToUser(Long userId, Message message) {
        List<Map<String, Object>> subscriptions = jdbc.queryForList(
                "select id, endpoint, p256dh, auth from push_subscriptions where user_id = ?", userId);
        byte[] payload = objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
        int delivered = 0;
        for (Map<String, Object> s : subscriptions) {
            if (send((Long) s.get("id"), (String) s.get("endpoint"), (String) s.get("p256dh"), (String) s.get("auth"), payload)) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean send(Long id, String endpoint, String p256dh, String auth, byte[] payload) {
        try {
            byte[] body = encryptor.encrypt(payload, p256dh, auth);
            int status = gateway.send(endpoint, body, Map.of(
                    "Content-Encoding", "aes128gcm",
                    "Content-Type", "application/octet-stream",
                    "TTL", "3600", // 기기가 꺼져 있으면 푸시 서버가 1시간까지 보관했다가 전달
                    "Urgency", "normal",
                    "Authorization", vapidKeys.authorization(endpoint)));
            if (status == 404 || status == 410) {
                // 사용자가 브라우저에서 알림을 끄거나 앱을 지운 경우: 구독을 정리한다
                jdbc.update("delete from push_subscriptions where id = ?", id);
                return false;
            }
            if (status >= 200 && status < 300) {
                jdbc.update("update push_subscriptions set last_success_at = ? where id = ?",
                        Timestamp.from(serviceTime.now()), id);
                return true;
            }
            return false;
        } catch (GeneralSecurityException | RuntimeException e) {
            log.warn("푸시를 보내지 못했습니다: subscription={}", id, e);
            return false;
        }
    }

    private void validate(String endpoint, String p256dh, String auth) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean allowedHost = allowedHosts.stream().anyMatch(allowed -> allowed.startsWith("*.")
                    ? host.endsWith(allowed.substring(1))
                    : host.equals(allowed));
            if (!"https".equals(uri.getScheme()) || !allowedHost || uri.getUserInfo() != null) {
                throw new BusinessException(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
            }
            EcKeys.publicFromRaw(EcKeys.fromBase64Url(p256dh));
            if (EcKeys.fromBase64Url(auth).length != 16) {
                throw new BusinessException(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
            }
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
        }
    }
}
