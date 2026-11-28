package com.habitcrew.domain.notification;

import com.habitcrew.global.time.ServiceTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 앱 안 알림함.
 * 푸시 알림은 브라우저 권한·기기에 따라 못 받을 수 있어서, 같은 내용을 앱 안에도 남겨 언제든 볼 수 있게 한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int LIST_SIZE = 30;
    static final Duration KEEP = Duration.ofDays(60);

    private final JdbcTemplate jdbc;
    private final ServiceTime serviceTime;

    public enum Type { COMMENT, LIKE, PENALTY, MEMBER_JOINED }

    public record Item(Long id, Type type, String title, String body, String url, Instant createdAt, boolean read) {
    }

    public record Inbox(int unreadCount, List<Item> items) {
    }

    public void create(Long userId, Type type, String title, String body, String url) {
        jdbc.update("insert into notifications (user_id, type, title, body, url, created_at) values (?, ?, ?, ?, ?, ?)",
                userId, type.name(), cut(title, 100), cut(body, 200), url, Timestamp.from(serviceTime.now()));
    }

    @Transactional(readOnly = true)
    public Inbox inbox(Long userId) {
        Integer unread = jdbc.queryForObject(
                "select count(*) from notifications where user_id = ? and read_at is null", Integer.class, userId);
        List<Item> items = jdbc.query("""
                        select id, type, title, body, url, created_at, read_at from notifications
                        where user_id = ? order by id desc limit ?
                        """,
                (rs, i) -> new Item(rs.getLong("id"), Type.valueOf(rs.getString("type")), rs.getString("title"),
                        rs.getString("body"), rs.getString("url"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") != null),
                userId, LIST_SIZE);
        return new Inbox(unread == null ? 0 : unread, items);
    }

    /** 알림함을 열면 모두 읽음으로 */
    public void markAllRead(Long userId) {
        jdbc.update("update notifications set read_at = ? where user_id = ? and read_at is null",
                Timestamp.from(serviceTime.now()), userId);
    }

    /** 60일 지난 알림 정리 (매일 정산 뒤에 부른다) */
    public int deleteOld() {
        return jdbc.update("delete from notifications where created_at < ?",
                Timestamp.from(serviceTime.now().minus(KEEP)));
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
