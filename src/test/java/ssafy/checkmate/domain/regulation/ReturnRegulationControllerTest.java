package ssafy.checkmate.domain.regulation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.global.auth.JwtTokenProvider;

/**
 * 귀국 규정 안내(34번) 엔드포인트 테스트.
 *
 * 외부 호출이 없는 고정 안내라 아무것도 모킹하지 않는다 — 서비스·표·시드를 모두 진짜로 태워
 * 앱이 뜨자마자 응답이 정상으로 나가는지까지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReturnRegulationControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String URL = "/api/v1/return-regulations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("귀국 규정을 요청하면 기내 반입 5건과 한국 입국 검역 5건을 응답한다")
    void getReturnRegulations() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cabin.length()").value(5))
                .andExpect(jsonPath("$.data.quarantine.length()").value(5));
    }

    @Test
    @DisplayName("기내 반입은 보조배터리부터 고도주까지 정해진 순서로, 판정과 안내 문구를 함께 내려준다")
    void cabinRegulations() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(jsonPath("$.data.cabin[0].name").value("보조배터리"))
                .andExpect(jsonPath("$.data.cabin[0].level").value("허용"))
                .andExpect(jsonPath("$.data.cabin[0].guide").isNotEmpty())
                .andExpect(jsonPath("$.data.cabin[1].name").value("액체·젤·스프레이(치약, 화장품 등)"))
                .andExpect(jsonPath("$.data.cabin[1].level").value("제한"))
                .andExpect(jsonPath("$.data.cabin[2].name").value("칼"))
                .andExpect(jsonPath("$.data.cabin[2].level").value("금지"))
                .andExpect(jsonPath("$.data.cabin[3].name").value("가위"))
                .andExpect(jsonPath("$.data.cabin[3].level").value("금지"))
                .andExpect(jsonPath("$.data.cabin[4].name").value("고도주"))
                .andExpect(jsonPath("$.data.cabin[4].level").value("제한"));
    }

    @Test
    @DisplayName("한국 입국 검역은 육류부터 멸종위기종 기념품까지 정해진 순서로, 판정과 안내 문구를 함께 내려준다")
    void quarantineRegulations() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(jsonPath("$.data.quarantine[0].name")
                        .value("육류·육가공품·유제품(육포, 소시지, 햄, 치즈 등)"))
                .andExpect(jsonPath("$.data.quarantine[0].level").value("금지"))
                .andExpect(jsonPath("$.data.quarantine[0].guide").isNotEmpty())
                .andExpect(jsonPath("$.data.quarantine[1].name").value("생과일·채소·식물·흙"))
                .andExpect(jsonPath("$.data.quarantine[1].level").value("금지"))
                .andExpect(jsonPath("$.data.quarantine[2].name").value("의약품·건강기능식품"))
                .andExpect(jsonPath("$.data.quarantine[2].level").value("제한"))
                .andExpect(jsonPath("$.data.quarantine[3].name").value("주류·담배·향수"))
                .andExpect(jsonPath("$.data.quarantine[3].level").value("제한"))
                .andExpect(jsonPath("$.data.quarantine[4].name")
                        .value("멸종위기종 기념품(악어·뱀 가죽, 산호, 상아, 거북 제품 등)"))
                .andExpect(jsonPath("$.data.quarantine[4].level").value("금지"));
    }

    @Test
    @DisplayName("응답 항목은 name·level·guide 세 필드만 가진다")
    void itemHasOnlyThreeFields() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(jsonPath("$.data.cabin[0].length()").value(3))
                .andExpect(jsonPath("$.data.quarantine[0].length()").value(3));
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401을 응답한다")
    void getReturnRegulationsWithoutToken() throws Exception {
        mockMvc.perform(get(URL))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
