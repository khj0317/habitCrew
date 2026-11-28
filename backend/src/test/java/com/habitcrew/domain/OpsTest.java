package com.habitcrew.domain;

import com.habitcrew.domain.certification.CertificationService;
import com.habitcrew.domain.push.EcKeys;
import com.habitcrew.support.ApiTestSupport;
import com.habitcrew.support.Await;
import com.habitcrew.support.RecordingPushGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 운영 기능: 활동 알림, 요청 ID, 요청 수 제한, 신고 */
class OpsTest extends ApiTestSupport {

    private static final Instant MON_NOON = Instant.parse("2030-01-07T03:00:00Z");
    private static final List<String> EVERY_DAY =
            List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    @Autowired
    CertificationService certificationService;

    @Autowired
    RecordingPushGateway gateway;

    @AfterEach
    void resetGateway() {
        gateway.reset();
    }

    private record Crew(long id, TestUser owner, List<TestUser> members, long ownerCertId) {
    }

    /** 크루장 + 크루원 n명, 크루장이 오늘 인증 하나 */
    private Crew crewWithOwnerCert(int members) throws Exception {
        clock.set(MON_NOON);
        TestUser owner = newUser();
        JsonNode crew = json(perform(post("/api/crews"), owner,
                Map.of("name", "운영 크루", "certDays", EVERY_DAY, "penaltyPoints", 0, "maxMembers", 30)));
        long id = crew.get("id").asLong();
        List<TestUser> list = new java.util.ArrayList<>();
        for (int i = 0; i < members; i++) {
            TestUser u = newUser();
            perform(post("/api/crews/join"), u, Map.of("inviteCode", crew.get("inviteCode").asString()));
            list.add(u);
        }
        long certId = certificationService.create(owner.id(), id, png(), "오늘도", MON_NOON).id();
        return new Crew(id, owner, list, certId);
    }

    private String subscribe(TestUser user) throws Exception {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        String endpoint = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
        perform(post("/api/push/subscriptions"), user, Map.of("endpoint", endpoint, "keys", Map.of(
                "p256dh", EcKeys.base64Url(EcKeys.rawPublic((ECPublicKey) EcKeys.generate().getPublic())),
                "auth", EcKeys.base64Url(auth)))).andExpect(status().isNoContent());
        return endpoint;
    }

    // ---------------- 활동 알림 ----------------

    @Test
    @DisplayName("활동 알림: 내 인증에 댓글이 달리면 알림이 가고, 좋아요는 하루 한 번만 간다")
    void activityNotifications() throws Exception {
        Crew crew = crewWithOwnerCert(2);
        String ownerPhone = subscribe(crew.owner());
        long certId = crew.ownerCertId();

        perform(put("/api/certifications/" + certId + "/like"), crew.members().get(0));
        perform(delete("/api/certifications/" + certId + "/like"), crew.members().get(0));
        perform(put("/api/certifications/" + certId + "/like"), crew.members().get(0)); // 다시 눌러도
        perform(put("/api/certifications/" + certId + "/like"), crew.members().get(1)); // 다른 사람이 눌러도
        perform(post("/api/certifications/" + certId + "/comments"), crew.members().get(1), Map.of("content", "멋져요"));

        Await.until(() -> gateway.sentTo(ownerPhone).size() >= 2);
        Await.settle();
        assertThat(gateway.sentTo(ownerPhone)).hasSize(2); // 좋아요 1번 + 댓글 1번
    }

    @Test
    @DisplayName("활동 알림: 내가 내 인증에 한 반응이나, 알림을 끈 사람에게는 보내지 않는다")
    void noSelfOrDisabledNotifications() throws Exception {
        Crew crew = crewWithOwnerCert(1);
        String ownerPhone = subscribe(crew.owner());
        long certId = crew.ownerCertId();

        perform(post("/api/certifications/" + certId + "/comments"), crew.owner(), Map.of("content", "셀프 댓글"));
        perform(put("/api/users/me/notifications"), crew.owner(), Map.of("activity", false));
        perform(post("/api/certifications/" + certId + "/comments"), crew.members().get(0), Map.of("content", "응원해요"));

        Await.settle();
        assertThat(gateway.sentTo(ownerPhone)).isEmpty();
    }

    // ---------------- 요청 ID ----------------

    @Test
    @DisplayName("요청 ID: 응답 헤더와 에러 본문에 같은 ID가 붙고, 앞에서 보낸 ID는 이어서 쓴다")
    void requestId() throws Exception {
        TestUser user = newUser();

        String id = perform(get("/api/crews/999999"), user)
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        perform(get("/api/crews/999999").header("X-Request-Id", "edge-proxy-12345"), user)
                .andExpect(header().string("X-Request-Id", "edge-proxy-12345"))
                .andExpect(jsonPath("$.requestId").value("edge-proxy-12345"));
        assertThat(id).isNotBlank();
        // 이상한 문자가 섞인 ID는 받지 않고 새로 만든다 (로그 오염 방지)
        perform(get("/api/crews/999999").header("X-Request-Id", "bad id\nINJECTED"), user)
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("bad id\nINJECTED")));
    }

    // ---------------- 요청 수 제한 ----------------

    @Test
    @DisplayName("요청 수 제한: 댓글은 1분에 20개까지, 넘으면 429와 Retry-After")
    void rateLimit() throws Exception {
        Crew crew = crewWithOwnerCert(1);
        TestUser spammer = crew.members().get(0);
        for (int i = 0; i < 20; i++) {
            perform(post("/api/certifications/" + crew.ownerCertId() + "/comments"), spammer, Map.of("content", "도배 " + i))
                    .andExpect(status().isCreated());
        }
        perform(post("/api/certifications/" + crew.ownerCertId() + "/comments"), spammer, Map.of("content", "21번째"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        // 다른 사람은 영향을 받지 않는다
        perform(post("/api/certifications/" + crew.ownerCertId() + "/comments"), crew.owner(), Map.of("content", "괜찮아요"))
                .andExpect(status().isCreated());
    }

    // ---------------- 신고 ----------------

    @Test
    @DisplayName("신고: 한 명이 신고하면 그대로, 두 명이 신고하면 사진이 가려진다. 작성자·크루장은 볼 수 있고 크루장이 되돌릴 수 있다")
    void reportHidesPhoto() throws Exception {
        clock.set(MON_NOON);
        Crew crew = crewWithOwnerCert(3);
        TestUser author = crew.members().get(0);
        long certId = certificationService.create(author.id(), crew.id(), png(), null, MON_NOON).id();
        String url = "/api/certifications/" + certId;

        perform(post(url + "/report"), crew.members().get(1), Map.of("reason", "INAPPROPRIATE"))
                .andExpect(jsonPath("$.hidden").value(false));
        perform(post(url + "/report"), crew.members().get(1), Map.of("reason", "SPAM"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        perform(post(url + "/report"), crew.members().get(2), Map.of("reason", "SPAM"))
                .andExpect(jsonPath("$.hidden").value(true));

        perform(get(url), crew.members().get(1))
                .andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.reportedByMe").value(true))
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        perform(get(url), author).andExpect(jsonPath("$.imageUrl").isNotEmpty());
        perform(get(url), crew.owner()).andExpect(jsonPath("$.imageUrl").isNotEmpty());

        perform(put(url + "/hidden"), crew.members().get(1), Map.of("hidden", false)).andExpect(status().isForbidden());
        perform(put(url + "/hidden"), crew.owner(), Map.of("hidden", false)).andExpect(jsonPath("$.hidden").value(false));
        perform(get(url), crew.members().get(1)).andExpect(jsonPath("$.imageUrl").isNotEmpty());
    }

    @Test
    @DisplayName("신고: 내 인증은 신고할 수 없고, 크루장이 신고하면 바로 가려진다")
    void reportRules() throws Exception {
        Crew crew = crewWithOwnerCert(1);
        TestUser member = crew.members().get(0);
        long certId = certificationService.create(member.id(), crew.id(), png(), null, MON_NOON).id();

        perform(post("/api/certifications/" + certId + "/report"), member, Map.of("reason", "OTHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REPORT_OWN"));
        perform(post("/api/certifications/" + certId + "/report"), crew.owner(), Map.of("reason", "UNRELATED"))
                .andExpect(jsonPath("$.hidden").value(true));
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
