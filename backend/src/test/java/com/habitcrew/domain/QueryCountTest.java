package com.habitcrew.domain;

import com.habitcrew.domain.certification.CertificationService;
import com.habitcrew.support.ApiTestSupport;
import com.habitcrew.support.QueryCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * N+1 쿼리 방지 확인: 데이터가 늘어나도 한 화면을 그리는 데 드는 SQL 수가 그대로인지 본다.
 * (목록을 돌면서 항목마다 쿼리를 한 번씩 더 보내면, 항목 수만큼 쿼리가 늘어난다)
 */
class QueryCountTest extends ApiTestSupport {

    private static final Instant MON_NOON = Instant.parse("2030-01-07T03:00:00Z");
    private static final List<String> EVERY_DAY =
            List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    @Autowired
    CertificationService certificationService;

    private record Crew(long id, TestUser owner, List<TestUser> members) {
    }

    /** 크루원 size명, 각자 오늘 인증 1개 */
    private Crew crewWith(int size) throws Exception {
        clock.set(MON_NOON);
        TestUser owner = newUser();
        JsonNode crew = json(perform(post("/api/crews"), owner,
                Map.of("name", "쿼리 크루", "certDays", EVERY_DAY, "penaltyPoints", 0, "maxMembers", 30)));
        long id = crew.get("id").asLong();
        List<TestUser> members = new ArrayList<>(List.of(owner));
        for (int i = 1; i < size; i++) {
            TestUser user = newUser();
            perform(post("/api/crews/join"), user, Map.of("inviteCode", crew.get("inviteCode").asString()));
            members.add(user);
        }
        for (TestUser member : members) {
            certificationService.create(member.id(), id, png(), "인증", MON_NOON);
        }
        return new Crew(id, owner, members);
    }

    private int queries(TestUser user, String url) throws Exception {
        return QueryCounter.count(() -> perform(get(url), user).andExpect(status().isOk())).queries();
    }

    @Test
    @DisplayName("피드·오늘 현황·크루원 기록·랭킹·크루 상세: 크루원이 2명이든 10명이든 SQL 수가 같다")
    void constantQueriesPerScreen() throws Exception {
        Crew small = crewWith(2);
        Crew large = crewWith(10);

        for (String path : List.of("/certifications", "/certifications/today", "/stats", "/ranking", "",
                "/settlements", "/calendar")) {
            int smallQueries = queries(small.owner(), "/api/crews/" + small.id() + path);
            int largeQueries = queries(large.owner(), "/api/crews/" + large.id() + path);
            assertThat(smallQueries).as("쿼리를 실제로 세고 있는지").isPositive();
            assertThat(largeQueries)
                    .as("GET /api/crews/{id}%s: 2명일 때 %d번, 10명일 때 %d번", path, smallQueries, largeQueries)
                    .isEqualTo(smallQueries);
        }
    }

    @Test
    @DisplayName("내 크루 목록: 크루가 1개든 5개든 SQL 수가 같다")
    void constantQueriesForMyCrews() throws Exception {
        TestUser one = newUser();
        TestUser five = newUser();
        perform(post("/api/crews"), one, Map.of("name", "크루", "certDays", EVERY_DAY, "penaltyPoints", 0, "maxMembers", 5));
        for (int i = 0; i < 5; i++) {
            perform(post("/api/crews"), five, Map.of("name", "크루" + i, "certDays", EVERY_DAY, "penaltyPoints", 0, "maxMembers", 5));
        }

        assertThat(queries(five, "/api/crews")).isEqualTo(queries(one, "/api/crews"));
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
