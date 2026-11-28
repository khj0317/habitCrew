package com.habitcrew.domain.notification;

import com.habitcrew.domain.certification.CertificationService;
import com.habitcrew.domain.settlement.SettlementService;
import com.habitcrew.support.ApiTestSupport;
import com.habitcrew.support.Await;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationTest extends ApiTestSupport {

    private static final Instant SUN_NOON = Instant.parse("2030-01-06T03:00:00Z");
    private static final Instant MON_NOON = Instant.parse("2030-01-07T03:00:00Z");
    private static final Instant TUE_00_05 = Instant.parse("2030-01-07T15:05:00Z");
    private static final LocalDate MON = LocalDate.of(2030, 1, 7);

    @Autowired
    CertificationService certificationService;

    @Autowired
    SettlementService settlementService;

    @Autowired
    NotificationService notificationService;

    @Autowired
    JdbcTemplate jdbc;

    private JsonNode inbox(TestUser user) throws Exception {
        return json(perform(get("/api/notifications"), user).andExpect(status().isOk()));
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("새 크루원·댓글·좋아요·벌금이 알림함에 쌓이고, 열면 모두 읽음이 된다")
    void collectsActivity() throws Exception {
        clock.set(SUN_NOON);
        TestUser owner = newUser();
        JsonNode crew = json(perform(post("/api/crews"), owner, Map.of(
                "name", "월요 크루", "certDays", List.of("MONDAY"), "penaltyPoints", 500, "maxMembers", 10)));
        long crewId = crew.get("id").asLong();
        TestUser member = newUser();
        perform(post("/api/crews/join"), member, Map.of("inviteCode", crew.get("inviteCode").asString()))
                .andExpect(status().isOk());

        // 크루장: 새 크루원 알림
        JsonNode ownerInbox = inbox(owner);
        assertThat(ownerInbox.get("unreadCount").asInt()).isEqualTo(1);
        assertThat(ownerInbox.get("items").get(0).get("type").asString()).isEqualTo("MEMBER_JOINED");
        assertThat(ownerInbox.get("items").get(0).get("body").asString()).contains(member.nickname());

        // 크루원의 인증에 크루장이 좋아요·댓글
        clock.set(MON_NOON);
        long certId = certificationService.create(member.id(), crewId, png(), null, MON_NOON).id();
        perform(put("/api/certifications/" + certId + "/like"), owner).andExpect(status().isOk());
        perform(post("/api/certifications/" + certId + "/comments"), owner, Map.of("content", "멋져요"))
                .andExpect(status().isCreated());
        Await.until(() -> count(member.id()) == 2);

        JsonNode memberInbox = inbox(member);
        assertThat(memberInbox.get("items").findValuesAsString("type")).containsExactly("COMMENT", "LIKE");
        assertThat(memberInbox.get("items").get(0).get("url").asString())
                .isEqualTo("/crews/" + crewId + "/certifications/" + certId);

        // 정산: 인증 안 한 크루장에게 벌금 알림
        clock.set(TUE_00_05);
        assertThat(settlementService.settle(crewId, MON)).isTrue();
        JsonNode penalty = inbox(owner).get("items").get(0);
        assertThat(penalty.get("type").asString()).isEqualTo("PENALTY");
        assertThat(penalty.get("title").asString()).isEqualTo("벌금 500P");
        assertThat(penalty.get("createdAt").asString()).isEqualTo("2030-01-07T15:05:00Z");
        assertThat(penalty.get("body").asString()).contains("월요 크루", "1월 7일");
        assertThat(penalty.get("url").asString()).isEqualTo("/crews/" + crewId + "/settlements");
        assertThat(inbox(member).get("items").findValuesAsString("type")).doesNotContain("PENALTY");

        // 모두 읽음
        perform(post("/api/notifications/read"), owner).andExpect(status().isNoContent());
        JsonNode read = inbox(owner);
        assertThat(read.get("unreadCount").asInt()).isZero();
        assertThat(read.get("items")).allMatch(i -> i.get("read").asBoolean());
        // 다른 사람의 알림은 그대로
        assertThat(inbox(member).get("unreadCount").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("내 인증에 내가 반응한 건 알림이 생기지 않고, 활동 알림을 꺼도 알림함에는 남는다")
    void selfActivityAndPushSetting() throws Exception {
        clock.set(MON_NOON);
        TestUser owner = newUser();
        long crewId = json(perform(post("/api/crews"), owner, Map.of(
                "name", "크루", "certDays", List.of("MONDAY"), "penaltyPoints", 0, "maxMembers", 10)))
                .get("id").asLong();
        long certId = certificationService.create(owner.id(), crewId, png(), null, MON_NOON).id();

        perform(put("/api/certifications/" + certId + "/like"), owner).andExpect(status().isOk());
        Await.settle();
        assertThat(count(owner.id())).isZero();

        TestUser member = newUser();
        jdbc.update("update users set notify_activity = false where id = ?", owner.id());
        jdbc.update("insert into crew_members (crew_id, user_id, role, joined_at) values (?, ?, 'MEMBER', now())",
                crewId, member.id());
        perform(post("/api/certifications/" + certId + "/comments"), member, Map.of("content", "굿"))
                .andExpect(status().isCreated());
        Await.until(() -> count(owner.id()) == 1);
    }

    @Test
    @DisplayName("60일 지난 알림은 정리된다")
    void deletesOld() throws Exception {
        clock.set(MON_NOON);
        TestUser user = newUser();
        notificationService.create(user.id(), NotificationService.Type.LIKE, "옛 알림", "내용", "/");
        clock.set(MON_NOON.plus(Duration.ofDays(30)));
        notificationService.create(user.id(), NotificationService.Type.LIKE, "새 알림", "내용", "/");

        clock.set(MON_NOON.plus(Duration.ofDays(61)));
        notificationService.deleteOld();
        assertThat(inbox(user).get("items").findValuesAsString("title")).containsExactly("새 알림");
    }

    @Test
    @DisplayName("로그인하지 않으면 볼 수 없다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    private int count(Long userId) {
        Integer n = jdbc.queryForObject("select count(*) from notifications where user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }
}
