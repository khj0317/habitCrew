package com.habitcrew.domain.auth;

import com.habitcrew.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("회원가입하면 액세스 토큰과 HttpOnly 리프레시 쿠키를 받고, 토큰으로 내 정보를 조회할 수 있다")
    void signupAndMe() throws Exception {
        String email = uniqueEmail();
        MvcResult result = signup(email, "Password1", uniqueNickname())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains(RefreshTokenCookie.NAME, "HttpOnly", "SameSite=Strict", "Path=/api/auth");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("이메일은 대소문자와 공백을 정리해서 저장하고, 같은 이메일로는 다시 가입할 수 없다")
    void duplicateEmail() throws Exception {
        String email = uniqueEmail();
        signup(email, "Password1", uniqueNickname()).andExpect(status().isCreated());

        signup("  " + email.toUpperCase() + " ", "Password1", uniqueNickname())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("비밀번호 규칙을 어기면 어떤 필드가 왜 틀렸는지 알려준다")
    void validation() throws Exception {
        signup("not-an-email", "short", "a")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("email", "password", "nickname")));
    }

    @Test
    @DisplayName("비밀번호를 5번 틀리면 맞는 비밀번호로도 잠시 로그인할 수 없다")
    void loginLock() throws Exception {
        String email = uniqueEmail();
        signup(email, "Password1", uniqueNickname());

        for (int i = 0; i < 5; i++) {
            login(email, "WrongPass1").andExpect(status().isUnauthorized());
        }
        login(email, "Password1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    @Test
    @DisplayName("리프레시 토큰은 한 번만 쓸 수 있다 (재사용하면 401)")
    void refreshRotation() throws Exception {
        MvcResult signup = signup(uniqueEmail(), "Password1", uniqueNickname()).andReturn();
        Cookie first = signup.getResponse().getCookie(RefreshTokenCookie.NAME);

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie(RefreshTokenCookie.NAME);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mockMvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("토큰 없이 보호된 API를 부르면 공통 에러 형식의 401을 받는다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private org.springframework.test.web.servlet.ResultActions signup(String email, String password, String nickname)
            throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("email", email, "password", password, "nickname", nickname));
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asString();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String uniqueNickname() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
