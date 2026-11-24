package com.habitcrew.domain.settlement;

import com.habitcrew.domain.certification.CertificationService;
import com.habitcrew.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettlementTest extends ApiTestSupport {

    // 2030-01-06 일요일 ~ 2030-01-12 토요일 (KST 정오 = 03:00Z)
    private static final Instant SUN_NOON = Instant.parse("2030-01-06T03:00:00Z");
    private static final Instant MON_NOON = Instant.parse("2030-01-07T03:00:00Z");
    private static final Instant TUE_00_05 = Instant.parse("2030-01-07T15:05:00Z");
    private static final Instant WED_NOON = Instant.parse("2030-01-09T03:00:00Z");
    private static final Instant FRI_NOON = Instant.parse("2030-01-11T03:00:00Z");
    private static final LocalDate MON = LocalDate.of(2030, 1, 7);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);

    @Autowired
    SettlementService settlementService;

    @Autowired
    SettlementRunner settlementRunner;

    @Autowired
    CertificationService certificationService;

    private record Crew(long id, String code, TestUser owner) {
    }

    /** 일요일에 월·수·금 크루(벌금 1000P)를 만든다 */
    private Crew newMonWedFriCrew() throws Exception {
        clock.set(SUN_NOON);
        TestUser owner = newUser();
        JsonNode crew = json(perform(post("/api/crews"), owner, Map.of(
                "name", "월수금 크루", "certDays", List.of("MONDAY", "WEDNESDAY", "FRIDAY"),
                "penaltyPoints", 1000, "maxMembers", 30)));
        return new Crew(crew.get("id").asLong(), crew.get("inviteCode").asString(), owner);
    }

    private TestUser joinAt(Crew crew, Instant when) throws Exception {
        clock.set(when);
        TestUser user = newUser();
        perform(post("/api/crews/join"), user, Map.of("inviteCode", crew.code())).andExpect(status().isOk());
        return user;
    }

    private void certify(Crew crew, TestUser user, Instant when) throws Exception {
        clock.set(when);
        certificationService.create(user.id(), crew.id(), png(), null, when);
    }

    private JsonNode history(Crew crew) throws Exception {
        return json(perform(get("/api/crews/" + crew.id() + "/settlements"), crew.owner()).andExpect(status().isOk()));
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("인증일에 인증하지 않은 크루원에게만 벌금을 매긴다")
    void penalizesOnlyMissedMembers() throws Exception {
        Crew crew = newMonWedFriCrew();
        TestUser diligent = joinAt(crew, SUN_NOON);
        TestUser lazy = joinAt(crew, SUN_NOON);
        certify(crew, diligent, MON_NOON);

        clock.set(TUE_00_05);
        assertThat(settlementService.settle(crew.id(), MON)).isTrue();

        JsonNode monday = history(crew).get("items").get(0);
        assertThat(monday.get("date").asString()).isEqualTo("2030-01-07");
        assertThat(monday.get("targetCount").asInt()).isEqualTo(3);
        assertThat(monday.get("certifiedCount").asInt()).isEqualTo(1);
        assertThat(monday.get("penalties").findValuesAsString("nickname"))
                .containsExactlyInAnyOrder(crew.owner().nickname(), lazy.nickname());
        assertThat(monday.get("penalties").findValues("points")).allMatch(p -> p.asInt() == 1000);

        perform(get("/api/crews/" + crew.id() + "/stats"), lazy)
                .andExpect(jsonPath("$.me.totalPenaltyPoints").value(1000))
                .andExpect(jsonPath("$.me.missedCount").value(1))
                .andExpect(jsonPath("$.me.totalCertifications").value(0));
    }

    @Test
    @DisplayName("멱등성: 같은 날을 여러 번 정산해도 벌금은 한 번만 매겨진다")
    void settleIsIdempotent() throws Exception {
        Crew crew = newMonWedFriCrew();
        joinAt(crew, SUN_NOON);

        clock.set(TUE_00_05);
        assertThat(settlementService.settle(crew.id(), MON)).isTrue();
        assertThat(settlementService.settle(crew.id(), MON)).isFalse();
        settlementRunner.runPending();
        settlementRunner.runPending();

        assertThat(history(crew).get("items").get(0).get("penalties").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("동시성: 서버 5대가 같은 크루·같은 날을 동시에 정산해도 한 곳만 정산한다")
    void concurrentSettle() throws Exception {
        Crew crew = newMonWedFriCrew();
        joinAt(crew, SUN_NOON);
        joinAt(crew, SUN_NOON);
        clock.set(TUE_00_05);

        List<Boolean> results = runConcurrently(5, () -> settlementService.settle(crew.id(), MON));

        assertThat(results).containsOnlyOnce(true);
        assertThat(history(crew).get("items").get(0).get("penalties").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("그날 참여한 사람은 첫날이라 벌금을 받지 않는다")
    void joinedThatDayIsExempt() throws Exception {
        Crew crew = newMonWedFriCrew();
        TestUser lateJoiner = joinAt(crew, MON_NOON);

        clock.set(TUE_00_05);
        settlementService.settle(crew.id(), MON);

        JsonNode monday = history(crew).get("items").get(0);
        assertThat(monday.get("targetCount").asInt()).isEqualTo(1); // 크루장만
        assertThat(monday.get("penalties").findValuesAsString("nickname")).doesNotContain(lateJoiner.nickname());
    }

    @Test
    @DisplayName("인증 요일이 아닌 날은 정산하지 않는다")
    void skipsRestDays() throws Exception {
        Crew crew = newMonWedFriCrew();
        clock.set(WED_NOON);

        assertThat(settlementService.crewsToSettle(TUE)).doesNotContain(crew.id());
        assertThat(settlementService.settle(crew.id(), TUE)).isFalse();
    }

    @Test
    @DisplayName("따라잡기: 서버가 며칠 꺼져 있었어도 다음 실행에서 놓친 인증일을 모두 정산한다")
    void catchUpMissedDays() throws Exception {
        Crew crew = newMonWedFriCrew();
        TestUser friend = joinAt(crew, SUN_NOON);
        certify(crew, friend, MON_NOON);
        // 월요일 밤부터 금요일 정오까지 서버가 꺼져 있었다고 가정하고, 금요일에 처음 돈다
        clock.set(FRI_NOON);

        settlementRunner.runPending();

        JsonNode items = history(crew).get("items");
        assertThat(items.findValuesAsString("date")).containsExactly("2030-01-09", "2030-01-07"); // 수, 월 (화·목은 쉬는 날)
        assertThat(items.get(0).get("penalties").size()).isEqualTo(2); // 수요일: 둘 다 안 함
        assertThat(items.get(1).get("penalties").size()).isEqualTo(1); // 월요일: 크루장만 안 함
    }

    @Test
    @DisplayName("아직 끝나지 않은 오늘은 정산할 수 없다")
    void cannotSettleToday() throws Exception {
        Crew crew = newMonWedFriCrew();
        clock.set(MON_NOON);

        assertThatThrownBy(() -> settlementService.settle(crew.id(), MON)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("크루원 기록: 연속 인증이 긴 순서로 보여 준다")
    void statsOrderedByStreak() throws Exception {
        Crew crew = newMonWedFriCrew();
        TestUser friend = joinAt(crew, SUN_NOON);
        certify(crew, friend, MON_NOON);
        certify(crew, friend, WED_NOON);
        certify(crew, crew.owner(), WED_NOON);
        clock.set(FRI_NOON); // 금요일은 아직 진행 중이라 끊긴 것으로 보지 않는다

        perform(get("/api/crews/" + crew.id() + "/stats"), crew.owner())
                .andExpect(jsonPath("$.members[0].userId").value(friend.id()))
                .andExpect(jsonPath("$.members[0].currentStreak").value(2))
                .andExpect(jsonPath("$.members[1].currentStreak").value(1))
                .andExpect(jsonPath("$.me.userId").value(crew.owner().id()));
    }

    @Test
    @DisplayName("크루원이 아니면 기록과 정산 내역을 볼 수 없다")
    void nonMember() throws Exception {
        Crew crew = newMonWedFriCrew();
        TestUser stranger = newUser();

        perform(get("/api/crews/" + crew.id() + "/stats"), stranger).andExpect(status().isNotFound());
        perform(get("/api/crews/" + crew.id() + "/settlements"), stranger).andExpect(status().isNotFound());
    }

    private static <T> List<T> runConcurrently(int count, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get());
        }
        executor.shutdown();
        return results;
    }
}
