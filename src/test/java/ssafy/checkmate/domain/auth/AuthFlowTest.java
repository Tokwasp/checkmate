package ssafy.checkmate.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 로그인 → 토큰 발급 → 토큰으로 보호된 API 호출 까지 한 사이클을 실제 HTTP 흐름으로 검증한다.
 * (test 프로필에서는 auth.dev-login.enabled=true 라서 DevAuthController 가 존재한다)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowTest {

    private static final String DEV_LOGIN_URL = "/api/v1/auth/dev-login";
    private static final String ME_URL = "/api/v1/auth/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("dev-login으로 받은 토큰으로 /auth/me를 호출하면 내 정보가 나온다")
    void loginThenMe() throws Exception {
        String accessToken = devLogin("flow@test.com");

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("flow@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("flow"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("같은 이메일로 다시 로그인하면 회원이 중복 생성되지 않는다")
    void loginTwiceReusesMember() throws Exception {
        devLogin("again@test.com");
        devLogin("again@test.com");

        assertThat(memberRepository.findByEmail("again@test.com")).isPresent();
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400")
    void invalidEmail() throws Exception {
        mockMvc.perform(post(DEV_LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일이 비어 있으면 400")
    void blankEmail() throws Exception {
        mockMvc.perform(post(DEV_LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    /** dev-login을 호출해 accessToken 문자열만 꺼내는 헬퍼 */
    private String devLogin(String email) throws Exception {
        String body = mockMvc.perform(post(DEV_LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("data").get("accessToken").asString();
    }
}
