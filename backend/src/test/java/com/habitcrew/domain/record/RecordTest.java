package com.habitcrew.domain.record;

import com.habitcrew.domain.certification.CertificationService;
import com.habitcrew.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecordTest extends ApiTestSupport {

    // 2030-01-06 일 ~ 01-12 토 (KST 정오)
    private static final Instant SUN = Instant.parse("2030-01-06T03:00:00Z");
    private static final Instant MON = Instant.parse("2030-01-07T03:00:00Z");
    private static final Instant TUE = Instant.parse("2030-01-08T03:00:00Z");
    private static final Instant WED = Instant.parse("2030-01-09T03:00:00Z");
    private static final Instant FRI = Instant.parse("2030-01-11T03:00:00Z");
    private static final YearMonth JAN_2030 = YearMonth.of(2030, 1);
    private static final List<String> EVERY_DAY =
            List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    @Autowired
    CertificationService certificationService;

    @Autowired
    StringRedisTemplate redis;

    private record Crew(long id, String code, TestUser owner) {
    }

    private Crew newCrew(List<String> days) throws Exception {
        clock.set(SUN);
        TestUser owner = newUser();
        JsonNode crew = json(perform(post("/api/crews"), owner,
                Map.of("name", "기록 크루", "certDays", days, "penaltyPoints", 0, "maxMembers", 10)));
        return new Crew(crew.get("id").asLong(), crew.get("inviteCode").asString(), owner);
    }

    private TestUser join(Crew crew) throws Exception {
        clock.set(SUN);
        TestUser user = newUser();
        perform(post("/api/crews/join"), user, Map.of("inviteCode", crew.code()));
        return user;
    }

    private long certify(Crew crew, TestUser user, Instant when) throws Exception {
        clock.set(when);
        return certificationService.create(user.id(), crew.id(), png(), null, when).id();
    }

    private JsonNode ranking(Crew crew) throws Exception {
        return json(perform(get("/api/crews/" + crew.id() + "/ranking?month=2030-01"), crew.owner())
                .andExpect(status().isOk())).get("entries");
    }

    @Test
    @DisplayName("월간 랭킹: 인증 횟수 순서이고, 같으면 공동 순위, 0회인 크루원도 나온다")
    void rankingOrder() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        TestUser a = join(crew);
        TestUser b = join(crew);
        certify(crew, a, MON);
        certify(crew, a, TUE);
        certify(crew, crew.owner(), MON);
        certify(crew, b, MON);

        JsonNode entries = ranking(crew);
        assertThat(entries.findValuesAsString("nickname")).first().isEqualTo(a.nickname());
        assertThat(entries.findValues("rank")).extracting(JsonNode::asInt).containsExactly(1, 2, 2);
        assertThat(entries.findValues("count")).extracting(JsonNode::asInt).containsExactly(2, 1, 1);
    }

    @Test
    @DisplayName("랭킹 캐시: 처음 조회 때 DB로 만들고, 이후 인증은 커밋 후 Redis에 바로 더해진다")
    void incrementalUpdate() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        ranking(crew); // 캐시를 만든다 (모두 0)
        assertThat(redis.hasKey(RankingService.readyKey(crew.id(), JAN_2030))).isTrue();

        certify(crew, crew.owner(), MON);
        Double score = redis.opsForZSet().score(RankingService.key(crew.id(), JAN_2030), String.valueOf(crew.owner().id()));
        assertThat(score).isEqualTo(1.0);

        // 오늘 올린 인증을 지우면 점수도 빠진다
        long id = certify(crew, crew.owner(), TUE);
        perform(delete("/api/certifications/" + id), crew.owner()).andExpect(status().isNoContent());
        assertThat(ranking(crew).get(0).get("count").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis가 비어도(재시작·만료) 다음 조회 때 DB에서 똑같이 다시 만든다")
    void rebuildsAfterRedisLoss() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        TestUser a = join(crew);
        certify(crew, a, MON);
        certify(crew, a, TUE);
        JsonNode before = ranking(crew);

        redis.delete(List.of(RankingService.key(crew.id(), JAN_2030), RankingService.readyKey(crew.id(), JAN_2030)));
        certify(crew, crew.owner(), WED); // 캐시가 없는 동안의 인증도 빠지지 않아야 한다

        JsonNode after = ranking(crew);
        assertThat(after.get(0).get("count").asInt()).isEqualTo(before.get(0).get("count").asInt());
        assertThat(after.get(1).get("count").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("크루를 나간 사람은 랭킹에서 빠진다")
    void leftMembersExcluded() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        TestUser leaver = join(crew);
        certify(crew, leaver, MON);

        perform(delete("/api/crews/" + crew.id() + "/members/me"), leaver).andExpect(status().isNoContent());

        assertThat(ranking(crew).findValuesAsString("nickname")).doesNotContain(leaver.nickname());
    }

    @Test
    @DisplayName("달력: 인증함·빼먹음·쉬는 날·오늘(아직)·미래·참여 전을 날마다 구분한다")
    void calendar() throws Exception {
        Crew crew = newCrew(List.of("MONDAY", "WEDNESDAY", "FRIDAY"));
        certify(crew, crew.owner(), MON);
        clock.set(FRI); // 수요일은 빼먹었고, 금요일은 아직 진행 중

        JsonNode calendar = json(perform(get("/api/crews/" + crew.id() + "/calendar?month=2030-01"), crew.owner()));
        JsonNode days = calendar.get("days");
        assertThat(days.size()).isEqualTo(31);
        assertThat(dayStatus(days, 5)).isEqualTo("NOT_JOINED"); // 1/5 토: 참여 전
        assertThat(dayStatus(days, 6)).isEqualTo("REST");       // 1/6 일: 참여한 날, 쉬는 요일
        assertThat(dayStatus(days, 7)).isEqualTo("CERTIFIED");  // 1/7 월
        assertThat(dayStatus(days, 8)).isEqualTo("REST");       // 1/8 화
        assertThat(dayStatus(days, 9)).isEqualTo("MISSED");     // 1/9 수
        assertThat(dayStatus(days, 11)).isEqualTo("PENDING");   // 1/11 금: 오늘
        assertThat(dayStatus(days, 14)).isEqualTo("FUTURE");    // 1/14 월
        assertThat(calendar.get("certifiedCount").asInt()).isEqualTo(1);
        assertThat(calendar.get("missedCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 크루원의 달력도 볼 수 있지만, 크루원이 아닌 사람 것은 볼 수 없다")
    void calendarOfOthers() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        TestUser friend = join(crew);
        TestUser stranger = newUser();

        perform(get("/api/crews/" + crew.id() + "/calendar?userId=" + friend.id()), crew.owner())
                .andExpect(jsonPath("$.nickname").value(friend.nickname()));
        perform(get("/api/crews/" + crew.id() + "/calendar?userId=" + stranger.id()), crew.owner())
                .andExpect(status().isNotFound());
        perform(get("/api/crews/" + crew.id() + "/ranking"), stranger).andExpect(status().isNotFound());
    }

    private static String dayStatus(JsonNode days, int dayOfMonth) {
        return days.get(dayOfMonth - 1).get("status").asString();
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
