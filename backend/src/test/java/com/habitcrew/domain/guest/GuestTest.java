package com.habitcrew.domain.guest;

import com.habitcrew.support.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestTest extends ApiTestSupport {

    @Autowired
    GuestCleanup cleanup;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Guest(TestUser user, Cookie refresh, long crewId) {
    }

    /** 테스트마다 다른 IP로 만들어 생성 제한이 서로 영향을 주지 않게 한다 */
    private MvcResult createGuest(String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/guest").with(r -> {
            r.setRemoteAddr(ip);
            return r;
        })).andReturn();
    }

    private Guest newGuest() throws Exception {
        MvcResult result = createGuest("10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        TestUser user = new TestUser(body.get("user").get("id").asLong(), body.get("user").get("nickname").asString(),
                body.get("accessToken").asString());
        long crewId = json(perform(get("/api/crews"), user)).get(0).get("id").asLong();
        return new Guest(user, result.getResponse().getCookie("habitcrew_refresh"), crewId);
    }

    @Test
    @DisplayName("체험하기: 로그인 상태가 되고, 2주치 기록이 채워진 체험 크루가 바로 보인다")
    void sandboxIsReady() throws Exception {
        MvcResult result = createGuest("10.1.0.1");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).get("user");
        assertThat(user.get("guest").asBoolean()).isTrue();
        assertThat(user.get("email").isNull()).isTrue();
        assertThat(Instant.parse(user.get("guestExpiresAt").asString()))
                .isBetween(Instant.now().plus(Duration.ofHours(23)), Instant.now().plus(Duration.ofHours(25)));
        assertThat(result.getResponse().getCookie("habitcrew_refresh")).isNotNull();

        TestUser guest = new TestUser(user.get("id").asLong(), user.get("nickname").asString(),
                objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString());
        JsonNode crews = json(perform(get("/api/crews"), guest));
        assertThat(crews.size()).isEqualTo(1);
        assertThat(crews.get(0).get("memberCount").asInt()).isEqualTo(4);
        assertThat(crews.get(0).get("myRole").asString()).isEqualTo("OWNER");
        long crewId = crews.get(0).get("id").asLong();

        // 내 기록: 최근 4일 연속, 2번 빼먹어 2,000P
        perform(get("/api/crews/" + crewId + "/stats"), guest)
                .andExpect(jsonPath("$.me.currentStreak").value(4))
                .andExpect(jsonPath("$.me.missedCount").value(2))
                .andExpect(jsonPath("$.me.totalPenaltyPoints").value(2000))
                .andExpect(jsonPath("$.members[0].nickname").value(org.hamcrest.Matchers.startsWith("책벌레")));
        // 오늘: 직접 올려 볼 수 있게 비워 두었고, 크루원 둘은 이미 인증
        perform(get("/api/crews/" + crewId + "/certifications/today"), guest)
                .andExpect(jsonPath("$.certDay").value(true))
                .andExpect(jsonPath("$.myCertificationId").doesNotExist())
                .andExpect(jsonPath("$.certifiedCount").value(2));
        // 피드·정산 내역·랭킹이 채워져 있다
        JsonNode feed = json(perform(get("/api/crews/" + crewId + "/certifications"), guest));
        assertThat(feed.get("items").size()).isEqualTo(10);
        assertThat(feed.get("nextCursor").isNull()).isFalse();
        assertThat(json(perform(get("/api/crews/" + crewId + "/settlements"), guest)).get("items").size()).isEqualTo(13);
        assertThat(json(perform(get("/api/crews/" + crewId + "/ranking"), guest)).get("entries").size()).isEqualTo(4);
        // 샘플 사진도 서명된 주소로 열린다
        assertThat(fetch(feed.get("items").get(0).get("thumbnailUrl").asString())).isEqualTo(200);
    }

    @Test
    @DisplayName("체험 계정은 실제 크루에 참여할 수 없다")
    void guestCannotJoinRealCrew() throws Exception {
        TestUser owner = newUser();
        String code = json(perform(post("/api/crews"), owner, Map.of("name", "실제 크루",
                "certDays", List.of("MONDAY"), "penaltyPoints", 0, "maxMembers", 5))).get("inviteCode").asString();

        perform(post("/api/crews/join"), newGuest().user(), Map.of("inviteCode", code))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUEST_RESTRICTED"));
    }

    @Test
    @DisplayName("실제 회원은 체험 크루의 초대 코드로 미리보기·참여할 수 없다")
    void realUserCannotJoinGuestCrew() throws Exception {
        Guest guest = newGuest();
        String code = json(perform(get("/api/crews/" + guest.crewId()), guest.user())).get("inviteCode").asString();
        TestUser real = newUser();

        perform(get("/api/crews/invites/" + code), real).andExpect(status().isNotFound());
        perform(post("/api/crews/join"), real, Map.of("inviteCode", code))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));
    }

    @Test
    @DisplayName("24시간이 지나면 체험 계정과 체험 크루, 직접 올린 사진이 지워지고 샘플 사진은 남는다")
    void cleanupAfterExpiry() throws Exception {
        Guest guest = newGuest();
        String sampleUrl = json(perform(get("/api/crews/" + guest.crewId() + "/certifications"), guest.user()))
                .get("items").get(0).get("thumbnailUrl").asString();
        JsonNode uploaded = json(mockMvc.perform(multipart("/api/crews/" + guest.crewId() + "/certifications")
                        .file(new MockMultipartFile("photo", "p.png", "image/png", png()))
                        .header("Authorization", guest.user().bearer()))
                .andExpect(status().isCreated()));

        clock.set(Instant.now().plus(Duration.ofHours(25)));
        assertThat(cleanup.run()).isGreaterThanOrEqualTo(4); // 체험 계정 + 가상 크루원 3명

        mockMvc.perform(post("/api/auth/refresh").cookie(guest.refresh())).andExpect(status().isUnauthorized());
        assertThat(fetch(uploaded.get("imageUrl").asString())).isEqualTo(404); // 직접 올린 사진은 지워짐
        assertThat(fetch(sampleUrl)).isEqualTo(200);                          // 같이 쓰는 샘플 사진은 남음
    }

    @Test
    @DisplayName("남용 방지: 같은 IP에서 1시간에 10개까지만 만들 수 있다")
    void rateLimited() throws Exception {
        String ip = "10.9." + UUID.randomUUID().hashCode() % 200 + ".9";
        for (int i = 0; i < 10; i++) {
            assertThat(createGuest(ip).getResponse().getStatus()).isEqualTo(201);
        }
        MvcResult limited = createGuest(ip);
        assertThat(limited.getResponse().getStatus()).isEqualTo(429);
        assertThat(limited.getResponse().getContentAsString()).contains("GUEST_LIMIT");
    }

    private int fetch(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
