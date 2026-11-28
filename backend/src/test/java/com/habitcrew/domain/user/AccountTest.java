package com.habitcrew.domain.user;

import com.habitcrew.support.ApiTestSupport;
import com.habitcrew.support.Await;
import com.habitcrew.support.RecordingMailer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountTest extends ApiTestSupport {

    @Autowired
    RecordingMailer mailer;

    private record Account(String email, String password, TestUser user, Cookie refresh) {
    }

    private Account signup() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "acct-" + suffix + "@example.com";
        MvcResult result = postJson("/api/auth/signup", Map.of("email", email, "password", "Password1",
                "nickname", "acct_" + suffix)).andExpect(status().isCreated()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        TestUser user = new TestUser(body.get("user").get("id").asLong(), body.get("user").get("nickname").asString(),
                body.get("accessToken").asString());
        return new Account(email, "Password1", user, result.getResponse().getCookie("habitcrew_refresh"));
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", password));
    }

    private static String tokenFrom(String mailText) {
        Matcher matcher = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)").matcher(mailText);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    // ---------------- 비밀번호 찾기 ----------------

    @Test
    @DisplayName("비밀번호 찾기: 메일 링크로 새 비밀번호를 정하면 예전 비밀번호와 다른 기기 로그인이 모두 무효가 된다")
    void passwordReset() throws Exception {
        Account account = signup();

        postJson("/api/auth/password-reset/request", Map.of("email", "  " + account.email().toUpperCase() + " "))
                .andExpect(status().isAccepted());
        Await.until(() -> mailer.lastTo(account.email()).isPresent());
        String token = tokenFrom(mailer.lastTo(account.email()).get().text());

        postJson("/api/auth/password-reset/confirm", Map.of("token", token, "newPassword", "NewPassword2"))
                .andExpect(status().isNoContent());

        login(account.email(), "Password1").andExpect(status().isUnauthorized());
        login(account.email(), "NewPassword2").andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").cookie(account.refresh())).andExpect(status().isUnauthorized());
        // 링크는 한 번만 쓸 수 있다
        postJson("/api/auth/password-reset/confirm", Map.of("token", token, "newPassword", "Another3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    @DisplayName("가입하지 않은 이메일로 비밀번호 찾기를 해도 같은 응답(202)이 오고 메일은 가지 않는다")
    void resetUnknownEmail() throws Exception {
        String email = "nobody-" + UUID.randomUUID() + "@example.com";

        postJson("/api/auth/password-reset/request", Map.of("email", email)).andExpect(status().isAccepted());

        Await.settle();
        assertThat(mailer.countTo(email)).isZero();
    }

    @Test
    @DisplayName("비밀번호 찾기 메일은 같은 이메일로 1시간에 3번까지만 보낸다 (메일 폭탄 방지)")
    void resetMailLimited() throws Exception {
        Account account = signup();
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/password-reset/request").with(r -> {
                r.setRemoteAddr("10.20.0." + (int) (Math.random() * 250)); // IP 요청 제한과 섞이지 않게
                return r;
            }).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + account.email() + "\"}"))
                    .andExpect(status().isAccepted());
        }
        Await.until(() -> mailer.countTo(account.email()) >= 3);
        Await.settle();
        assertThat(mailer.countTo(account.email())).isEqualTo(3);
    }

    // ---------------- 로그인한 사용자 ----------------

    @Test
    @DisplayName("비밀번호 바꾸기: 현재 비밀번호가 맞아야 하고, 바꾸면 다른 기기는 로그아웃되고 지금 기기는 새 토큰을 받는다")
    void changePassword() throws Exception {
        Account account = signup();

        perform(put("/api/users/me/password"), account.user(), Map.of("currentPassword", "Wrong1234", "newPassword", "NewPassword2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        MvcResult changed = perform(put("/api/users/me/password"), account.user(),
                Map.of("currentPassword", "Password1", "newPassword", "NewPassword2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        mockMvc.perform(post("/api/auth/refresh").cookie(account.refresh())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(changed.getResponse().getCookie("habitcrew_refresh")))
                .andExpect(status().isOk());
        login(account.email(), "NewPassword2").andExpect(status().isOk());
    }

    @Test
    @DisplayName("닉네임 바꾸기: 중복·예약된 이름은 안 되고, 체험 계정은 바꿀 수 없다")
    void changeNickname() throws Exception {
        Account account = signup();
        Account other = signup();
        String newName = "새이름_" + UUID.randomUUID().toString().substring(0, 4);

        perform(patch("/api/users/me"), account.user(), Map.of("nickname", "  " + newName + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(newName));
        perform(patch("/api/users/me"), account.user(), Map.of("nickname", other.user().nickname()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
        perform(patch("/api/users/me"), account.user(), Map.of("nickname", "체험러_가짜"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVED_NICKNAME"));

        JsonNode guest = objectMapper.readTree(mockMvc.perform(post("/api/auth/guest").with(r -> {
            r.setRemoteAddr("10.30.0." + (int) (Math.random() * 250));
            return r;
        })).andReturn().getResponse().getContentAsString());
        TestUser guestUser = new TestUser(guest.get("user").get("id").asLong(), guest.get("user").get("nickname").asString(),
                guest.get("accessToken").asString());
        perform(patch("/api/users/me"), guestUser, Map.of("nickname", "바꿔볼래"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUEST_ACCOUNT_READONLY"));
    }

    @Test
    @DisplayName("가입할 때도 예약된 닉네임은 쓸 수 없다")
    void reservedNicknameOnSignup() throws Exception {
        postJson("/api/auth/signup", Map.of("email", "r-" + UUID.randomUUID() + "@example.com", "password", "Password1",
                "nickname", "탈퇴한사용자_1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVED_NICKNAME"));
    }

    @Test
    @DisplayName("알림 설정을 끄고 켤 수 있다")
    void notificationSetting() throws Exception {
        Account account = signup();

        perform(put("/api/users/me/notifications"), account.user(), Map.of("activity", false))
                .andExpect(jsonPath("$.notifyActivity").value(false));
        perform(get("/api/users/me"), account.user()).andExpect(jsonPath("$.notifyActivity").value(false));
    }

    // ---------------- 탈퇴 ----------------

    @Test
    @DisplayName("탈퇴: 크루원이 남은 크루의 크루장이면 먼저 넘겨야 하고, 탈퇴하면 로그인·재발급이 막히고 같은 이메일로 다시 가입할 수 있다")
    void deleteAccount() throws Exception {
        Account owner = signup();
        Account friend = signup();
        JsonNode crew = json(perform(post("/api/crews"), owner.user(), Map.of("name", "탈퇴 크루",
                "certDays", List.of("MONDAY"), "penaltyPoints", 0, "maxMembers", 5)));
        long crewId = crew.get("id").asLong();
        perform(post("/api/crews/join"), friend.user(), Map.of("inviteCode", crew.get("inviteCode").asString()));

        perform(delete("/api/users/me"), owner.user(), Map.of("password", "Password1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_OF_ACTIVE_CREWS"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("탈퇴 크루")));
        perform(delete("/api/users/me"), owner.user(), Map.of("password", "Wrong1234"))
                .andExpect(status().isBadRequest());

        perform(post("/api/crews/" + crewId + "/owner"), owner.user(), Map.of("userId", friend.user().id()));
        perform(delete("/api/users/me"), owner.user(), Map.of("password", "Password1")).andExpect(status().isNoContent());

        login(owner.email(), "Password1").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(owner.refresh())).andExpect(status().isUnauthorized());
        perform(get("/api/crews/" + crewId), friend.user())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.myRole").value("OWNER"));
        postJson("/api/auth/signup", Map.of("email", owner.email(), "password", "Password1",
                "nickname", "다시가입_" + UUID.randomUUID().toString().substring(0, 4)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("체험 계정은 탈퇴·비밀번호 변경을 할 수 없다")
    void guestCannotChangeAccount() throws Exception {
        JsonNode guest = objectMapper.readTree(mockMvc.perform(post("/api/auth/guest").with(r -> {
            r.setRemoteAddr("10.31.0." + (int) (Math.random() * 250));
            return r;
        })).andReturn().getResponse().getContentAsString());
        TestUser guestUser = new TestUser(guest.get("user").get("id").asLong(), guest.get("user").get("nickname").asString(),
                guest.get("accessToken").asString());

        perform(delete("/api/users/me"), guestUser, Map.of("password", "anything1"))
                .andExpect(status().isForbidden());
        perform(put("/api/users/me/password"), guestUser, Map.of("currentPassword", "a", "newPassword", "Password12"))
                .andExpect(status().isForbidden());
    }
}
