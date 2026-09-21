package ssafy.checkmate.domain.checklist;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.service.ItemAnalysis;
import ssafy.checkmate.domain.checklist.service.ItemAnalysisClient;
import ssafy.checkmate.domain.checklist.service.QuarantineRegulationClient;
import ssafy.checkmate.domain.checklist.service.QuarantineVerdict;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;
import ssafy.checkmate.global.auth.JwtTokenProvider;

/**
 * 촬영 물품 반입 판정(35번) 엔드포인트 테스트.
 *
 * 실제 GMS·Bedrock 대신 두 클라이언트만 모킹하고 서비스는 진짜로 태운다 — 30번 컨트롤러 테스트와 같은 방식이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QuarantineCheckControllerTest {

    private static final String URL = "/api/v1/scan/quarantine-check";
    private static final ItemAnalysis JERKY =
            new ItemAnalysis("육포", "육류 가공품", List.of("돼지고기", "소고기"), "건조 가공");

    private Long memberId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ItemAnalysisClient itemAnalysisClient;

    @MockitoBean
    private QuarantineRegulationClient quarantineRegulationClient;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(Member.builder()
                .email("owner@example.com")
                .nickname("tester")
                .profileImageUrl("https://example.com/p.png")
                .provider(Provider.GOOGLE)
                .build()).getId();
    }

    @Test
    @DisplayName("사진을 올리면 읽어 낸 물품과 판정을 응답한다")
    void check() throws Exception {
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.of(JERKY));
        given(quarantineRegulationClient.judge(JERKY)).willReturn(QuarantineVerdict.of(RegulationLevel.PROHIBITED,
                "육류 가공품은 축산물 검역 대상이라 휴대 반입이 불가해요.",
                "적발 시 위반 횟수·원산지에 따라 100만~1,000만 원의 과태료가 부과될 수 있어요.",
                "소지하고 있다면 공항 검역대에 자진 신고하세요."));

        mockMvc.perform(multipart(URL).file(image("image/jpeg"))
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("성공"))
                .andExpect(jsonPath("$.data.item.name").value("육포"))
                .andExpect(jsonPath("$.data.item.category").value("육류 가공품"))
                .andExpect(jsonPath("$.data.item.ingredients[0]").value("돼지고기"))
                .andExpect(jsonPath("$.data.item.ingredients[1]").value("소고기"))
                .andExpect(jsonPath("$.data.item.state").value("건조 가공"))
                .andExpect(jsonPath("$.data.verdict.level").value("금지"))
                .andExpect(jsonPath("$.data.verdict.reason").value("육류 가공품은 축산물 검역 대상이라 휴대 반입이 불가해요."))
                .andExpect(jsonPath("$.data.verdict.penalty").isNotEmpty())
                .andExpect(jsonPath("$.data.verdict.action").value("소지하고 있다면 공항 검역대에 자진 신고하세요."));
    }

    @Test
    @DisplayName("허용 판정이면 과태료와 조치가 없다")
    void checkAllowed() throws Exception {
        ItemAnalysis doll = new ItemAnalysis("캐릭터 인형", "일반 공산품", List.of("폴리에스터", "솜"), "완제품");
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.of(doll));
        given(quarantineRegulationClient.judge(doll)).willReturn(
                QuarantineVerdict.of(RegulationLevel.ALLOWED, "검역 대상이 아닌 일반 공산품이에요.", null, null));

        mockMvc.perform(multipart(URL).file(image("image/png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict.level").value("허용"))
                .andExpect(jsonPath("$.data.verdict.reason").value("검역 대상이 아닌 일반 공산품이에요."))
                .andExpect(jsonPath("$.data.verdict.penalty").doesNotExist())
                .andExpect(jsonPath("$.data.verdict.action").doesNotExist());
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401을 응답한다")
    void checkWithoutToken() throws Exception {
        mockMvc.perform(multipart(URL).file(image("image/jpeg")))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이미지 파트가 없으면 400을 응답한다")
    void checkWithoutImage() throws Exception {
        mockMvc.perform(multipart(URL).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("물체 이미지가 유효하지 않습니다."));
    }

    @Test
    @DisplayName("지원하지 않는 형식이면 400을 응답한다")
    void checkWithUnsupportedImage() throws Exception {
        mockMvc.perform(multipart(URL).file(image("image/gif"))
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미지는 5MB 이하의 JPEG, PNG, WebP만 올릴 수 있습니다."));
    }

    @Test
    @DisplayName("사진에서 물품을 찾지 못하면 422를 응답한다")
    void checkWhenItemNotRecognized() throws Exception {
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.empty());

        mockMvc.perform(multipart(URL).file(image("image/jpeg"))
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andDo(print())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("물품을 인식하지 못했습니다."));
    }

    @Test
    @DisplayName("호출 횟수를 모두 썼으면 429를 응답한다")
    void checkWhenQuotaExhausted() throws Exception {
        entityManager.createQuery("update Member m set m.itemAnalysisCount = 20 where m.id = :id")
                .setParameter("id", memberId).executeUpdate();

        mockMvc.perform(multipart(URL).file(image("image/jpeg"))
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("물품 분석 횟수를 모두 사용했습니다."));
    }

    @Test
    @DisplayName("물품 분석이 실패하면 500을 응답한다")
    void checkWhenAnalysisFails() throws Exception {
        given(itemAnalysisClient.analyze(any())).willThrow(new RuntimeException("GMS 호출 실패"));

        mockMvc.perform(multipart(URL).file(image("image/jpeg"))
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("물품 이미지 분석에 실패했습니다."));
    }

    @Test
    @DisplayName("규정 검색·판정이 실패하면 500을 응답한다")
    void checkWhenRegulationFails() throws Exception {
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.of(JERKY));
        given(quarantineRegulationClient.judge(any())).willThrow(new RuntimeException("Bedrock 호출 실패"));

        mockMvc.perform(multipart(URL).file(image("image/jpeg"))
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("규정 검사에 실패했습니다."));
    }

    private MockMultipartFile image(String contentType) {
        return new MockMultipartFile("image", "photo", contentType, new byte[] {1, 2, 3});
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
