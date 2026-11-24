package com.habitcrew.domain.crew;

import com.habitcrew.domain.crew.dto.CrewRequests;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CrewApiTest extends ApiTestSupport {

    @Autowired
    CrewService crewService;

    @Autowired
    CrewMemberRepository memberRepository;

    private Map<String, Object> crewBody(int maxMembers) {
        return Map.of(
                "name", "아침 6시 기상",
                "description", "  매일 기상 인증  ",
                "certDays", List.of("MONDAY", "WEDNESDAY", "FRIDAY"),
                "penaltyPoints", 1000,
                "maxMembers", maxMembers);
    }

    private JsonNode createCrew(TestUser owner, int maxMembers) throws Exception {
        return json(perform(post("/api/crews"), owner, crewBody(maxMembers)).andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("크루를 만들면 만든 사람이 크루장이 되고 8자리 초대 코드가 생긴다")
    void create() throws Exception {
        TestUser owner = newUser();

        perform(post("/api/crews"), owner, crewBody(5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("아침 6시 기상"))
                .andExpect(jsonPath("$.description").value("매일 기상 인증"))
                .andExpect(jsonPath("$.certDays", hasItems("MONDAY", "WEDNESDAY", "FRIDAY")))
                .andExpect(jsonPath("$.certDays.length()").value(3))
                .andExpect(jsonPath("$.inviteCode").value(org.hamcrest.Matchers.matchesPattern("[2-9A-HJKMNP-Z]{8}")))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.members[0].userId").value(owner.id()));
    }

    @Test
    @DisplayName("인증 요일을 고르지 않거나 정원이 범위를 벗어나면 만들 수 없다")
    void createValidation() throws Exception {
        TestUser owner = newUser();
        Map<String, Object> body = Map.of("name", "a", "certDays", List.of(), "penaltyPoints", -1, "maxMembers", 100);

        perform(post("/api/crews"), owner, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("name", "certDays", "penaltyPoints", "maxMembers")));
    }

    @Test
    @DisplayName("초대 코드로 미리보기 후 참여하면 내 크루 목록에 인원 수와 함께 보인다")
    void previewAndJoin() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        String code = createCrew(owner, 5).get("inviteCode").asString();
        String messyCode = " " + code.substring(0, 4).toLowerCase() + "-" + code.substring(4) + " ";

        perform(get("/api/crews/invites/" + messyCode.strip()), friend)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.ownerNickname").value(owner.nickname()))
                .andExpect(jsonPath("$.alreadyJoined").value(false))
                .andExpect(jsonPath("$.inviteCode").doesNotExist())
                .andExpect(jsonPath("$.members").doesNotExist());

        perform(post("/api/crews/join"), friend, Map.of("inviteCode", messyCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));

        perform(get("/api/crews"), friend)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].memberCount").value(2))
                .andExpect(jsonPath("$[0].myRole").value("MEMBER"));

        perform(post("/api/crews/join"), friend, Map.of("inviteCode", code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
    }

    @Test
    @DisplayName("없는 초대 코드로는 참여할 수 없다")
    void invalidCode() throws Exception {
        perform(post("/api/crews/join"), newUser(), Map.of("inviteCode", "ZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));
    }

    @Test
    @DisplayName("크루원이 아니면 크루가 있는지조차 알 수 없다 (403이 아닌 404)")
    void nonMemberCannotSeeCrew() throws Exception {
        long crewId = createCrew(newUser(), 5).get("id").asLong();

        perform(get("/api/crews/" + crewId), newUser())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREW_NOT_FOUND"));
    }

    @Test
    @DisplayName("동시성: 남은 자리가 2개인데 10명이 동시에 참여해도 정확히 2명만 들어간다")
    void concurrentJoinRespectsCapacity() throws Exception {
        TestUser owner = newUser();
        JsonNode crew = createCrew(owner, 3);
        long crewId = crew.get("id").asLong();
        String code = crew.get("inviteCode").asString();

        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            users.add(newUser());
        }

        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<ErrorCode> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(users.size(), i -> {
            try {
                crewService.join(users.get(i).id(), new CrewRequests.Join(code));
                success.incrementAndGet();
            } catch (BusinessException e) {
                failures.add(e.getErrorCode());
            }
        });

        assertThat(success.get()).isEqualTo(2);
        assertThat(failures).hasSize(8).containsOnly(ErrorCode.CREW_FULL);
        assertThat(memberRepository.countByCrewId(crewId)).isEqualTo(3);
    }

    @Test
    @DisplayName("동시성: 같은 사람이 참여 버튼을 5번 연타해도 한 번만 참여된다")
    void concurrentDuplicateJoin() throws Exception {
        JsonNode crew = createCrew(newUser(), 10);
        long crewId = crew.get("id").asLong();
        String code = crew.get("inviteCode").asString();
        TestUser friend = newUser();

        AtomicInteger success = new AtomicInteger();
        runConcurrently(5, i -> {
            try {
                crewService.join(friend.id(), new CrewRequests.Join(code));
                success.incrementAndGet();
            } catch (BusinessException e) {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ALREADY_JOINED);
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(memberRepository.countByCrewId(crewId)).isEqualTo(2);
    }

    @Test
    @DisplayName("크루장만 정보를 바꿀 수 있고, 정원을 지금 인원보다 줄일 수는 없다")
    void update() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        JsonNode crew = createCrew(owner, 5);
        long crewId = crew.get("id").asLong();
        perform(post("/api/crews/join"), friend, Map.of("inviteCode", crew.get("inviteCode").asString()));

        Map<String, Object> body = Map.of("name", "저녁 러닝", "description", "", "maxMembers", 4);
        perform(patch("/api/crews/" + crewId), friend, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CREW_OWNER"));

        perform(patch("/api/crews/" + crewId), owner, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("저녁 러닝"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.penaltyPoints").value(1000));

        perform(patch("/api/crews/" + crewId), owner, Map.of("name", "저녁 러닝", "maxMembers", 1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("초대 코드를 새로 만들면 예전 코드로는 참여할 수 없다")
    void regenerateInviteCode() throws Exception {
        TestUser owner = newUser();
        JsonNode crew = createCrew(owner, 5);
        String oldCode = crew.get("inviteCode").asString();

        String newCode = json(perform(post("/api/crews/" + crew.get("id").asLong() + "/invite-code"), owner)
                .andExpect(status().isOk())).get("inviteCode").asString();

        assertThat(newCode).isNotEqualTo(oldCode);
        perform(post("/api/crews/join"), newUser(), Map.of("inviteCode", oldCode))
                .andExpect(status().isNotFound());
        perform(post("/api/crews/join"), newUser(), Map.of("inviteCode", newCode))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("크루장은 다른 크루원이 있으면 나갈 수 없고, 크루장을 넘긴 뒤에는 나갈 수 있다")
    void ownerLeaveAndTransfer() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        JsonNode crew = createCrew(owner, 5);
        long crewId = crew.get("id").asLong();
        perform(post("/api/crews/join"), friend, Map.of("inviteCode", crew.get("inviteCode").asString()));

        perform(delete("/api/crews/" + crewId + "/members/me"), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));

        perform(post("/api/crews/" + crewId + "/owner"), owner, Map.of("userId", friend.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.members[0].userId").value(friend.id()));

        perform(delete("/api/crews/" + crewId + "/members/me"), owner).andExpect(status().isNoContent());
        perform(get("/api/crews/" + crewId), friend)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    @DisplayName("혼자 남은 크루장이 나가면 크루가 사라진다")
    void lastOwnerLeaveDeletesCrew() throws Exception {
        TestUser owner = newUser();
        long crewId = createCrew(owner, 5).get("id").asLong();

        perform(delete("/api/crews/" + crewId + "/members/me"), owner).andExpect(status().isNoContent());
        perform(get("/api/crews"), owner).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("크루장은 크루원을 내보낼 수 있지만, 자기 자신은 내보낼 수 없다")
    void kick() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        JsonNode crew = createCrew(owner, 5);
        long crewId = crew.get("id").asLong();
        perform(post("/api/crews/join"), friend, Map.of("inviteCode", crew.get("inviteCode").asString()));

        perform(delete("/api/crews/" + crewId + "/members/" + owner.id()), friend)
                .andExpect(status().isForbidden());
        perform(delete("/api/crews/" + crewId + "/members/" + owner.id()), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_TARGET_SELF"));
        perform(delete("/api/crews/" + crewId + "/members/" + friend.id()), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1));
        perform(get("/api/crews/" + crewId), friend).andExpect(status().isNotFound());
    }

    private interface IndexedTask {
        void run(int index);
    }

    /** 모든 스레드가 준비될 때까지 기다렸다가 한꺼번에 출발시킨다 */
    private static void runConcurrently(int count, IndexedTask task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < count; i++) {
            int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run(index);
                } catch (Throwable t) {
                    unexpected.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(unexpected).isEmpty();
    }
}
