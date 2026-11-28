package com.habitcrew.domain.guest;

import com.habitcrew.domain.certification.CertificationPhotos;
import com.habitcrew.global.storage.PhotoStorage;
import com.habitcrew.global.time.ServiceTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 만료된 체험 계정과 그 계정이 만든 모든 것을 지운다.
 * 체험 계정은 실제 크루에 들어갈 수 없고 실제 회원도 체험 크루에 들어올 수 없으므로, 지워도 실제 데이터에는 영향이 없다.
 * 체험 중에 직접 올린 사진은 저장소에서도 지우고, 여럿이 같이 쓰는 샘플 사진은 남긴다.
 */
@Slf4j
@Component
public class GuestCleanup {

    private final JdbcTemplate jdbc;
    private final PhotoStorage storage;
    private final ServiceTime serviceTime;

    public GuestCleanup(JdbcTemplate jdbc, PhotoStorage storage, ServiceTime serviceTime) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.serviceTime = serviceTime;
    }

    /** @return 지운 체험 계정 수 (가상 크루원 포함) */
    @Transactional
    public int run() {
        Timestamp now = Timestamp.from(serviceTime.now());
        String expired = "select id from users where guest_expires_at < ?";

        // 1) 체험 중에 직접 올린 사진 (샘플 사진 제외)
        List<Map<String, Object>> uploaded = jdbc.queryForList("""
                select c.image_key, c.thumbnail_key from certifications c
                where (c.user_id in (%1$s) or c.crew_id in (select id from crews where owner_id in (%1$s)))
                  and c.image_key not like ?
                """.formatted(expired), now, now, CertificationPhotos.SAMPLE_PREFIX + "%");

        // 2) 체험 크루를 지우면 크루원·인증·좋아요·댓글·정산·벌금이 함께 지워진다 (ON DELETE CASCADE)
        jdbc.update("delete from crews where owner_id in (" + expired + ")", now);
        // 3) 혹시 남은 흔적 (체험 계정이 만든 다른 크루의 기록 등)
        for (String table : List.of("certification_likes", "certification_comments", "certification_reports",
                "penalties", "crew_members", "certifications", "notifications")) {
            jdbc.update("delete from " + table + " where user_id in (" + expired + ")", now);
        }
        // 4) 계정 (푸시 구독·알림 기록은 ON DELETE CASCADE)
        int deleted = jdbc.update("delete from users where guest_expires_at < ?", now);

        for (Map<String, Object> photo : uploaded) {
            storage.delete((String) photo.get("image_key"));
            storage.delete((String) photo.get("thumbnail_key"));
        }
        if (deleted > 0) {
            log.info("만료된 체험 계정 {}개를 정리했습니다 (사진 {}장)", deleted, uploaded.size());
        }
        return deleted;
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "habitcrew.guest.cleanup-enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduler {

        private final GuestCleanup cleanup;

        Scheduler(GuestCleanup cleanup) {
            this.cleanup = cleanup;
        }

        /** 매시 17분 */
        @Scheduled(cron = "0 17 * * * *")
        public void run() {
            try {
                cleanup.run();
            } catch (RuntimeException e) {
                log.error("체험 계정 정리 실패", e);
            }
        }
    }
}
