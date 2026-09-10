package ssafy.checkmate.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.member.repository.MemberRepository;

/**
 * 필터 → 리졸버 → 컨트롤러가 실제로 연결되는지 확인하는 통합 테스트.
 *
 * @SpringBootTest: 애플리케이션 컨텍스트 전체를 띄운다 (AuthConfig가 등록한 필터 포함).
 * @AutoConfigureMockMvc: 실제 Tomcat 없이 HTTP 요청을 흉내 내는 MockMvc를 준비한다.
 *                        기본값 addFilters=true 라서 FilterRegistrationBean으로 등록한 필터도 함께 탄다.
 * @Transactional: 테스트마다 트랜잭션을 열고 끝나면 롤백해서, 저장한 Member가 다음 테스트에 남지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthenticationFilterTest {

    private static final String ME_URL = "/api/v1/auth/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("토큰 없이 보호된 경로에 접근하면 401과 ApiResponse 형태의 본문을 받는다")
    void noToken() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("위조된 토큰이면 401 '유효하지 않은 토큰입니다.'")
    void invalidToken() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer this.is.forged"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));
    }

    @Test
    @DisplayName("Bearer 접두어가 없으면 토큰이 없는 것으로 취급한다")
    void missingBearerPrefix() throws Exception {
        String token = jwtTokenProvider.createAccessToken(1L, Role.USER);

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("만료된 토큰이면 401 '만료된 토큰입니다.'")
    void expiredToken() throws Exception {
        // 같은 secret, 만료 시간만 음수로 준 provider로 "이미 만료된" 토큰을 만든다
        JwtTokenProvider expiredProvider = new JwtTokenProvider(
                new JwtProperties(jwtProperties.secret(), Duration.ofSeconds(-10)));
        String token = expiredProvider.createAccessToken(1L, Role.USER);

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("만료된 토큰입니다."));
    }

    @Test
    @DisplayName("유효한 토큰이면 필터를 통과해 컨트롤러가 내 정보를 반환한다")
    void validToken() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .email("user@test.com")
                .nickname("tester")
                .provider(Provider.KAKAO)
                .build());
        String token = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(member.getId()))
                .andExpect(jsonPath("$.data.email").value("user@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("tester"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("화이트리스트 경로는 토큰 없이도 필터에 막히지 않는다")
    void whitelistBypassesFilter() throws Exception {
        // dev-login 컨트롤러는 아직 없어서 404지만, 핵심은 401이 아니라는 것
        mockMvc.perform(post("/api/v1/auth/dev-login"))
                .andExpect(statusIsNot(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("/api 밖의 경로는 필터 대상이 아니다")
    void outsideApiPathBypassesFilter() throws Exception {
        mockMvc.perform(get("/h2-console"))
                .andExpect(statusIsNot(HttpStatus.UNAUTHORIZED));
    }

    private static ResultMatcher statusIsNot(HttpStatus status) {
        return result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(status.value());
    }
}
