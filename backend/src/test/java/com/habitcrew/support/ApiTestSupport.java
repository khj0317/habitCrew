package com.habitcrew.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** API 통합 테스트 공통 도우미: 테스트마다 새 사용자를 만들어 서로 영향을 주지 않게 한다 */
@IntegrationTest
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected record TestUser(Long id, String nickname, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected TestUser newUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "user-" + suffix + "@example.com",
                "password", "Password1",
                "nickname", "user_" + suffix));
        String response = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return new TestUser(json.get("user").get("id").asLong(), json.get("user").get("nickname").asString(),
                json.get("accessToken").asString());
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, TestUser user, Object body) throws Exception {
        request.header("Authorization", user.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, TestUser user) throws Exception {
        return perform(request, user, null);
    }

    protected JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
