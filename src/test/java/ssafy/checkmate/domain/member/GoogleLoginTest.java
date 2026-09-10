package ssafy.checkmate.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.domain.member.service.oauth.GoogleIdTokenParser;
import ssafy.checkmate.domain.member.service.oauth.GoogleUserInfo;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoogleLoginTest {

    private static final String LOGIN_URL = "/api/v1/auth/google";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private GoogleIdTokenParser googleIdTokenParser;

    @Test
    @DisplayName("신규 회원이면 저장하고 accessToken과 회원 정보를 내려준다")
    void newMember() throws Exception {
        given(googleIdTokenParser.parse(anyString()))
                .willReturn(new GoogleUserInfo("google@test.com", "구글유저", "https://img/p.png"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"dummy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.member.email").value("google@test.com"))
                .andExpect(jsonPath("$.data.member.nickname").value("구글유저"))
                .andExpect(jsonPath("$.data.member.profileImageUrl").value("https://img/p.png"))
                .andExpect(jsonPath("$.data.member.provider").value("GOOGLE"));

        Member saved = memberRepository.findByEmail("google@test.com").orElseThrow();
        assertThat(saved.getProvider()).isEqualTo(Provider.GOOGLE);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 회원을 재사용하고 isNewUser는 false다")
    void existingMember() throws Exception {
        given(googleIdTokenParser.parse(anyString()))
                .willReturn(new GoogleUserInfo("again@test.com", "어게인", null));

        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"dummy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true));

        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"dummy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));

        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("idToken이 비어 있으면 400")
    void blankIdToken() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
