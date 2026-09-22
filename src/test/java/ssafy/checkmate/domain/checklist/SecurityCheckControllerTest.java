package ssafy.checkmate.domain.checklist;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.CarriageType;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.service.AviationRegulationClient;
import ssafy.checkmate.domain.checklist.service.ItemTranslationClient;
import ssafy.checkmate.domain.checklist.service.SecurityVerdict;
import ssafy.checkmate.domain.checklist.service.VerdictLevel;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.auth.JwtTokenProvider;

/**
 * 반입 규정 검사(30번) 엔드포인트 테스트.
 *
 * 실제 Bedrock 대신 {@link AviationRegulationClient}만 모킹하고 서비스는 진짜로 태운다 —
 * 체크리스트 컨트롤러 테스트가 추천 엔진을 모킹하는 방식과 같다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityCheckControllerTest {

    /** 반입 검사는 호출 횟수를 회원 행에 기록하므로 실제 Member가 있어야 한다. */
    private Long MEMBER_ID;
    private Long OTHER_MEMBER_ID;

    @BeforeEach
    void setUpMembers() {
        MEMBER_ID = saveMember("owner@example.com").getId();
        OTHER_MEMBER_ID = saveMember("other@example.com").getId();
    }

    private Member saveMember(String email) {
        return memberRepository.save(Member.builder()
                .email(email)
                .nickname("tester")
                .profileImageUrl("https://example.com/p.png")
                .provider(Provider.GOOGLE)
                .build());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AviationRegulationClient aviationRegulationClient;

    /** 검색어 번역도 실제 GMS를 부르지 않는다 — 여기서 보는 건 HTTP 응답 형식이다. */
    @MockitoBean
    private ItemTranslationClient itemTranslationClient;

    @Test
    @DisplayName("반입 규정 검사를 요청하면 개수 3종과 물품 목록을 응답한다")
    void check() throws Exception {
        Trip trip = createTrip(MEMBER_ID, "일본");
        Bag cabin = createBag(trip, "기내백", CarriageType.CABIN);
        BagItem knife = createItem(trip, cabin, "맥가이버칼");
        BagItem passport = createItem(trip, cabin, "여권");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of(
                new SecurityVerdict(knife.getId(), VerdictLevel.VIOLATION,
                        "맥가이버칼은 날이 있어 기내 반입이 불가해요. 위탁 수하물로 옮기면 가지고 갈 수 있어요."),
                new SecurityVerdict(passport.getId(), VerdictLevel.NONE, null)));

        mockMvc.perform(post("/api/v1/trips/{tripId}/security-check", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violationCount").value(1))
                .andExpect(jsonPath("$.data.warningCount").value(0))
                .andExpect(jsonPath("$.data.passCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].name").value("맥가이버칼"))
                .andExpect(jsonPath("$.data.items[0].riskLevel").value("금지"))
                .andExpect(jsonPath("$.data.items[0].guide").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].bagName").value("기내백"))
                .andExpect(jsonPath("$.data.items[1].name").value("여권"))
                .andExpect(jsonPath("$.data.items[1].riskLevel").value("통과"))
                .andExpect(jsonPath("$.data.items[1].guide").doesNotExist());
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401을 응답한다")
    void checkWithoutToken() throws Exception {
        Trip trip = createTrip(MEMBER_ID, "일본");

        mockMvc.perform(post("/api/v1/trips/{tripId}/security-check", trip.getId()))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("검사할 물품이 없으면 400을 응답한다")
    void checkWithNoItems() throws Exception {
        Trip trip = createTrip(MEMBER_ID, "일본");

        mockMvc.perform(post("/api/v1/trips/{tripId}/security-check", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("검사할 물품이 없습니다."));
    }

    @Test
    @DisplayName("본인 여행이 아니면 403을 응답한다")
    void checkOtherMembersTrip() throws Exception {
        Trip trip = createTrip(MEMBER_ID, "일본");

        mockMvc.perform(post("/api/v1/trips/{tripId}/security-check", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 여행만 검사할 수 있습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 여행이면 404를 응답한다")
    void checkMissingTrip() throws Exception {
        mockMvc.perform(post("/api/v1/trips/{tripId}/security-check", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 여행입니다."));
    }

    @Test
    @DisplayName("지식베이스가 다루지 않는 국가면 422를 응답한다")
    void checkUnsupportedCountry() throws Exception {
        Trip trip = createTrip(MEMBER_ID, "브라질");
        Bag cabin = createBag(trip, "기내백", CarriageType.CABIN);
        createItem(trip, cabin, "여권");

        mockMvc.perform(post("/api/v1/trips/{tripId}/security-check", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("아직 지원하지 않는 국가입니다."));
    }

    @Test
    @DisplayName("규정 조회가 실패하면 500을 응답한다")
    void checkWhenClientFails() throws Exception {
        Trip trip = createTrip(MEMBER_ID, "일본");
        Bag cabin = createBag(trip, "기내백", CarriageType.CABIN);
        createItem(trip, cabin, "여권");
        given(aviationRegulationClient.judge(any(), any()))
                .willThrow(new RuntimeException("Bedrock 호출 실패"));

        mockMvc.perform(post("/api/v1/trips/{tripId}/security-check", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("규정 검사에 실패했습니다."));
    }

    private Trip createTrip(Long memberId, String country) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .country(country)
                .region("오사카")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .purposes(List.of("휴가"))
                .latitude(34.6937)
                .longitude(135.5023)
                .build());
    }

    private Bag createBag(Trip trip, String name, CarriageType carriageType) {
        Bag bag = Bag.create(trip.getId(), MEMBER_ID, name);
        bag.changeCarriageType(carriageType);
        return bagRepository.save(bag);
    }

    /** 검사 대상은 가방에 담은 물품이므로 테스트 물품도 담은 상태로 만든다. */
    private BagItem createItem(Trip trip, Bag bag, String name) {
        BagItem item = BagItem.create(trip.getId(), MEMBER_ID, name, "기타", 1, bag.getId());
        item.update(null, null, null, true, null);
        return bagItemRepository.save(item);
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
