package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.response.SecurityCheckItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.SecurityCheckResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.BagItemRiskLevel;
import ssafy.checkmate.domain.checklist.entity.CarriageType;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.domain.checklist.entity.RiskCheckStep;
import ssafy.checkmate.domain.checklist.entity.RiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskLevelType;
import ssafy.checkmate.domain.checklist.exception.NoItemsToCheckException;
import ssafy.checkmate.domain.checklist.exception.SecurityCheckFailedException;
import ssafy.checkmate.domain.checklist.exception.SecurityCheckLimitExceededException;
import ssafy.checkmate.domain.checklist.exception.UnsupportedCountryException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagItemRiskLevelRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.repository.RiskLevelRepository;
import ssafy.checkmate.domain.checklist.service.AviationRegulationClient;
import ssafy.checkmate.domain.checklist.service.ItemTranslationClient;
import ssafy.checkmate.domain.checklist.service.SecurityCheckService;
import ssafy.checkmate.domain.checklist.service.SecurityCheckTarget;
import ssafy.checkmate.domain.checklist.service.SecurityVerdict;
import ssafy.checkmate.domain.checklist.service.VerdictLevel;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;

/**
 * 반입 규정 검사(30번) 서비스 테스트.
 *
 * 판정은 실제 Bedrock을 부르지 않고 {@link AviationRegulationClient}를 모킹해서 주입한다 —
 * 실제 호출은 돈이 나가고 CI에 자격증명이 없다. 실호출 확인은
 * {@code AviationRegulationClientManualIT}가 따로 맡는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SecurityCheckServiceTest {

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
    private SecurityCheckService securityCheckService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Autowired
    private BagItemRiskLevelRepository bagItemRiskLevelRepository;

    @Autowired
    private RiskLevelRepository riskLevelRepository;

    @MockitoBean
    private AviationRegulationClient aviationRegulationClient;

    /**
     * 번역도 실제 GMS를 부르지 않는다. 기본은 빈 결과 — 대부분의 테스트는 검색어 조립이 아니라
     * 판정 적용을 보므로, 번역이 필요한 테스트만 따로 스텁한다.
     */
    @MockitoBean
    private ItemTranslationClient itemTranslationClient;

    @Test
    @DisplayName("금지·주의·통과가 섞인 물품을 검사하면 개수 3종과 물품 전체 목록을 돌려준다")
    void checkReturnsCountsAndAllItems() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem knife = saveItem(trip, cabin, "맥가이버칼");
        BagItem cosmetic = saveItem(trip, cabin, "화장품");
        BagItem passport = saveItem(trip, cabin, "여권");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of(
                new SecurityVerdict(knife.getId(), VerdictLevel.VIOLATION, "기내 반입이 불가해요."),
                new SecurityVerdict(cosmetic.getId(), VerdictLevel.WARNING, "100ml까지만 가능해요."),
                new SecurityVerdict(passport.getId(), VerdictLevel.NONE, null)));

        // when
        SecurityCheckResponse response = securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getViolationCount()).isEqualTo(1);
        assertThat(response.getWarningCount()).isEqualTo(1);
        assertThat(response.getPassCount()).isEqualTo(1);
        assertThat(response.getItems()).hasSize(3)
                .extracting(SecurityCheckItemResponse::getName, SecurityCheckItemResponse::getRiskLevel)
                .containsExactly(
                        tuple("맥가이버칼", "금지"),
                        tuple("화장품", "주의"),
                        tuple("여권", "통과"));
    }

    @Test
    @DisplayName("통과 물품의 guide는 null이고, 금지·주의 물품은 모델이 준 문구를 그대로 내려준다")
    void checkFillsGuideOnlyForRiskyItems() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem knife = saveItem(trip, cabin, "맥가이버칼");
        BagItem passport = saveItem(trip, cabin, "여권");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of(
                new SecurityVerdict(knife.getId(), VerdictLevel.VIOLATION,
                        "날 길이가 6cm를 넘는 도검류는 기내 반입이 불가해요. 위탁 수하물로 옮기면 반입할 수 있어요."),
                new SecurityVerdict(passport.getId(), VerdictLevel.NONE, null)));

        // when
        SecurityCheckResponse response = securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        assertThat(guideOf(response, knife.getId()))
                .isEqualTo("날 길이가 6cm를 넘는 도검류는 기내 반입이 불가해요. 위탁 수하물로 옮기면 반입할 수 있어요.");
        assertThat(guideOf(response, passport.getId())).isNull();
    }

    @Test
    @DisplayName("금지·주의 판정은 bag_item_risk_level에 SECURITY_CHECK 단계로 저장한다")
    void checkSavesRiskLevelWithSecurityCheckStep() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem knife = saveItem(trip, cabin, "맥가이버칼");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of(
                new SecurityVerdict(knife.getId(), VerdictLevel.VIOLATION, "기내 반입이 불가해요.")));

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        BagItemRiskLevel saved = bagItemRiskLevelRepository
                .findByBagItemIdAndStep(knife.getId(), RiskCheckStep.SECURITY_CHECK).orElseThrow();
        assertThat(riskLevelRepository.findById(saved.getRiskLevelId()).orElseThrow().getLevel())
                .isEqualTo(RiskLevelType.VIOLATION);
    }

    @Test
    @DisplayName("통과 판정이면 그 물품의 SECURITY_CHECK 단계 기존 행을 지운다")
    void checkRemovesRiskLevelWhenPassed() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem item = saveItem(trip, cabin, "여권");
        RiskLevel warning = riskLevelRepository.findByLevel(RiskLevelType.WARNING).orElseThrow();
        bagItemRiskLevelRepository.save(
                BagItemRiskLevel.create(item.getId(), warning.getId(), RiskCheckStep.SECURITY_CHECK));
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of(
                new SecurityVerdict(item.getId(), VerdictLevel.NONE, null)));

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        assertThat(bagItemRiskLevelRepository.findByBagItemIdAndStep(item.getId(), RiskCheckStep.SECURITY_CHECK))
                .isEmpty();
    }

    @Test
    @DisplayName("물품 인식(26번)이 저장한 ITEM_RECOGNITION 위험도는 덮어쓰지 않는다")
    void checkKeepsItemRecognitionRiskLevel() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem item = saveItem(trip, cabin, "보조배터리");
        RiskLevel warning = riskLevelRepository.findByLevel(RiskLevelType.WARNING).orElseThrow();
        bagItemRiskLevelRepository.save(
                BagItemRiskLevel.create(item.getId(), warning.getId(), RiskCheckStep.ITEM_RECOGNITION));
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of(
                new SecurityVerdict(item.getId(), VerdictLevel.NONE, null)));

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        assertThat(bagItemRiskLevelRepository.findByBagItemIdAndStep(item.getId(), RiskCheckStep.ITEM_RECOGNITION))
                .isPresent();
    }

    @Test
    @DisplayName("같은 가방에 이름이 같은 물품이 여러 개면 한 묶음으로 묻고 판정을 모든 행에 적용한다")
    void checkGroupsSameNameInSameBag() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem first = saveItem(trip, cabin, "보조배터리");
        BagItem second = saveItem(trip, cabin, "보조배터리");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of(
                new SecurityVerdict(first.getId(), VerdictLevel.VIOLATION, "위탁이 불가해요.")));

        // when
        SecurityCheckResponse response = securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        ArgumentCaptor<List<SecurityCheckTarget>> captor = ArgumentCaptor.captor();
        verify(aviationRegulationClient).judge(any(), captor.capture());
        assertThat(captor.getValue()).hasSize(1);

        assertThat(response.getViolationCount()).isEqualTo(2);
        assertThat(riskLevelOf(response, second.getId())).isEqualTo("금지");
    }

    @Test
    @DisplayName("같은 이름이라도 가방이 다르면 따로 묻는다")
    void checkSeparatesSameNameInDifferentBags() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        Bag checked = saveBag(trip, "캐리어", CarriageType.CHECKED);
        saveItem(trip, cabin, "보조배터리");
        saveItem(trip, checked, "보조배터리");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of());

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        ArgumentCaptor<List<SecurityCheckTarget>> captor = ArgumentCaptor.captor();
        verify(aviationRegulationClient).judge(any(), captor.capture());
        assertThat(captor.getValue()).hasSize(2)
                .extracting(SecurityCheckTarget::getBagName)
                .containsExactlyInAnyOrder("기내백", "캐리어");
    }

    @Test
    @DisplayName("가방의 기내·위탁 구분이 아직 없으면 미지정으로 물어본다")
    void checkSendsUnspecifiedCarriageType() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag bag = saveBag(trip, "내 캐리어", null);
        saveItem(trip, bag, "여권");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of());

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        ArgumentCaptor<List<SecurityCheckTarget>> captor = ArgumentCaptor.captor();
        verify(aviationRegulationClient).judge(any(), captor.capture());
        assertThat(captor.getValue()).extracting(SecurityCheckTarget::getCarriageType)
                .containsExactly(SecurityCheckTarget.UNSPECIFIED);
    }

    @Test
    @DisplayName("판정이 안 온 물품은 통과로 두고 검사를 끝낸다")
    void checkTreatsMissingVerdictAsPass() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem answered = saveItem(trip, cabin, "맥가이버칼");
        BagItem missing = saveItem(trip, cabin, "양말");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of(
                new SecurityVerdict(answered.getId(), VerdictLevel.VIOLATION, "기내 반입이 불가해요.")));

        // when
        SecurityCheckResponse response = securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getPassCount()).isEqualTo(1);
        assertThat(riskLevelOf(response, missing.getId())).isEqualTo("통과");
        assertThat(guideOf(response, missing.getId())).isNull();
    }

    @Test
    @DisplayName("요청에 없던 itemId가 응답에 섞여 와도 무시하고 검사를 끝낸다")
    void checkIgnoresUnknownItemId() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem item = saveItem(trip, cabin, "여권");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of(
                new SecurityVerdict(item.getId(), VerdictLevel.NONE, null),
                new SecurityVerdict(999_999L, VerdictLevel.VIOLATION, "있을 수 없는 물품이에요.")));

        // when
        SecurityCheckResponse response = securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getViolationCount()).isZero();
        assertThat(response.getPassCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("담기지 않은 물품은 검사 대상에서 뺀다")
    void checkExcludesUnpackedItems() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem packed = saveItem(trip, cabin, "맥가이버칼");
        BagItem unpacked = saveUnpackedItem(trip, cabin, "여권");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of());

        // when
        SecurityCheckResponse response = securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        assertThat(unpacked.isDepartureChecked()).isFalse();
        assertThat(response.getItems()).extracting(SecurityCheckItemResponse::getItemId)
                .containsExactly(packed.getId());

        ArgumentCaptor<List<SecurityCheckTarget>> captor = ArgumentCaptor.captor();
        verify(aviationRegulationClient).judge(any(), captor.capture());
        assertThat(captor.getValue()).extracting(SecurityCheckTarget::getName)
                .containsExactly("맥가이버칼");
    }

    @Test
    @DisplayName("체크리스트에 물품이 있어도 담긴 게 하나도 없으면 검사하지 않는다")
    void checkRejectsTripWithNothingPacked() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveUnpackedItem(trip, cabin, "여권");

        // when & then
        assertThatThrownBy(() -> securityCheckService.check(trip.getId(), MEMBER_ID))
                .isInstanceOf(NoItemsToCheckException.class);
        verify(aviationRegulationClient, never()).judge(any(), any());
        assertThat(countOf(MEMBER_ID)).isZero();
    }

    @Test
    @DisplayName("물품 이름을 여행 국가 언어로 옮겨 한국어와 함께 검색어로 싣는다")
    void checkCarriesTranslatedTermsInSearchQuery() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "맥가이버칼");
        given(itemTranslationClient.translate(any(), any()))
                .willReturn(Map.of("맥가이버칼", "十徳ナイフ 刃物 凶器"));
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of());

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        ArgumentCaptor<List<SecurityCheckTarget>> captor = ArgumentCaptor.captor();
        verify(aviationRegulationClient).judge(any(), captor.capture());
        assertThat(captor.getValue()).singleElement()
                .extracting(SecurityCheckTarget::searchTerms)
                .isEqualTo("맥가이버칼 十徳ナイフ 刃物 凶器");
    }

    @Test
    @DisplayName("마스터에 없는 물품도 번역해 검색어에 싣는다 — 23종 밖이라고 한국어로만 찾지 않는다")
    void checkTranslatesItemsOutsideMaster() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "손톱깎이");
        given(itemTranslationClient.translate(any(), any()))
                .willReturn(Map.of("손톱깎이", "爪切り 刃物"));
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of());

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        ArgumentCaptor<List<SecurityCheckTarget>> captor = ArgumentCaptor.captor();
        verify(aviationRegulationClient).judge(any(), captor.capture());
        assertThat(captor.getValue()).singleElement()
                .extracting(SecurityCheckTarget::searchTerms)
                .isEqualTo("손톱깎이 爪切り 刃物");
    }

    @Test
    @DisplayName("번역을 못 받으면 한국어 이름만으로 검색한다 — 검사 자체는 계속한다")
    void checkFallsBackToKoreanOnlyWhenTranslationMissing() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "맥가이버칼");
        given(itemTranslationClient.translate(any(), any())).willReturn(Map.of());
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of());

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        ArgumentCaptor<List<SecurityCheckTarget>> captor = ArgumentCaptor.captor();
        verify(aviationRegulationClient).judge(any(), captor.capture());
        assertThat(captor.getValue()).singleElement()
                .extracting(SecurityCheckTarget::searchTerms)
                .isEqualTo("맥가이버칼");
    }

    @Test
    @DisplayName("한국행은 규정 문서가 한국어라 번역을 부르지 않는다")
    void checkSkipsTranslationForKorea() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "대한민국");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "맥가이버칼");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of());

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        verify(itemTranslationClient, never()).translate(any(), any());
    }

    @Test
    @DisplayName("여행 국가를 지식베이스 코드로 바꿔 질의한다")
    void checkPassesMappedJurisdiction() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "여권");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of());

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        ArgumentCaptor<CountryJurisdiction> captor = ArgumentCaptor.captor();
        verify(aviationRegulationClient).judge(captor.capture(), any());
        assertThat(captor.getValue()).isEqualTo(CountryJurisdiction.JP);
    }

    @Test
    @DisplayName("검사할 물품이 하나도 없으면 400으로 거절한다")
    void checkRejectsEmptyTrip() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");

        // when & then
        assertThatThrownBy(() -> securityCheckService.check(trip.getId(), MEMBER_ID))
                .isInstanceOf(NoItemsToCheckException.class)
                .hasMessage("검사할 물품이 없습니다.");
    }

    @Test
    @DisplayName("지식베이스가 다루지 않는 국가면 422로 거절한다")
    void checkRejectsUnsupportedCountry() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "브라질");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "여권");

        // when & then
        assertThatThrownBy(() -> securityCheckService.check(trip.getId(), MEMBER_ID))
                .isInstanceOf(UnsupportedCountryException.class)
                .hasMessage("아직 지원하지 않는 국가입니다.");
    }

    @Test
    @DisplayName("지원하지 않는 국가는 물품 조회보다 먼저 걸러 규정 호출을 하지 않는다")
    void checkRejectsUnsupportedCountryBeforeCallingClient() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "브라질");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "여권");

        // when & then
        assertThatThrownBy(() -> securityCheckService.check(trip.getId(), MEMBER_ID))
                .isInstanceOf(UnsupportedCountryException.class);
        verify(aviationRegulationClient, never())
                .judge(any(), any());
    }

    @Test
    @DisplayName("본인 여행이 아니면 403으로 거절한다")
    void checkRejectsOtherMembersTrip() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");

        // when & then
        assertThatThrownBy(() -> securityCheckService.check(trip.getId(), OTHER_MEMBER_ID))
                .isInstanceOf(TripAccessDeniedException.class)
                .hasMessage("본인의 여행만 검사할 수 있습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 여행이면 404로 거절한다")
    void checkRejectsMissingTrip() {
        // when & then
        assertThatThrownBy(() -> securityCheckService.check(999_999L, MEMBER_ID))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    @DisplayName("규정 조회가 실패하면 500으로 끝낸다")
    void checkFailsWhenClientThrows() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "여권");
        given(aviationRegulationClient.judge(any(), any()))
                .willThrow(new RuntimeException("Bedrock 호출 실패"));

        // when & then
        assertThatThrownBy(() -> securityCheckService.check(trip.getId(), MEMBER_ID))
                .isInstanceOf(SecurityCheckFailedException.class)
                .hasMessage("규정 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("호출 횟수를 모두 쓰면 429로 거절하고 규정 조회를 하지 않는다")
    void checkRejectsWhenQuotaExhausted() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "여권");
        exhaustQuota(MEMBER_ID);

        // when & then
        assertThatThrownBy(() -> securityCheckService.check(trip.getId(), MEMBER_ID))
                .isInstanceOf(SecurityCheckLimitExceededException.class)
                .hasMessage("반입 규정 검사 횟수를 모두 사용했습니다.");
        verify(aviationRegulationClient, never()).judge(any(), any());
    }

    @Test
    @DisplayName("검사에 성공하면 호출 횟수가 1 늘어난다")
    void checkIncreasesQuotaOnSuccess() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "여권");
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of());

        // when
        securityCheckService.check(trip.getId(), MEMBER_ID);

        // then
        assertThat(countOf(MEMBER_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("규정 조회가 실패해도 소모한 호출 횟수는 되돌리지 않는다")
    void checkKeepsQuotaWhenClientFails() {
        // given
        Trip trip = saveTrip(MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveItem(trip, cabin, "여권");
        given(aviationRegulationClient.judge(any(), any()))
                .willThrow(new RuntimeException("Bedrock 호출 실패"));

        // when & then
        assertThatThrownBy(() -> securityCheckService.check(trip.getId(), MEMBER_ID))
                .isInstanceOf(SecurityCheckFailedException.class);
        assertThat(countOf(MEMBER_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("검증에 걸려 거절된 요청은 호출 횟수를 쓰지 않는다")
    void checkDoesNotConsumeQuotaOnRejectedRequest() {
        // given
        Trip unsupported = saveTrip(MEMBER_ID, "브라질");
        Bag cabin = saveBag(unsupported, "기내백", CarriageType.CABIN);
        saveItem(unsupported, cabin, "여권");
        Trip empty = saveTrip(MEMBER_ID, "일본");

        // when & then
        assertThatThrownBy(() -> securityCheckService.check(unsupported.getId(), MEMBER_ID))
                .isInstanceOf(UnsupportedCountryException.class);
        assertThatThrownBy(() -> securityCheckService.check(empty.getId(), MEMBER_ID))
                .isInstanceOf(NoItemsToCheckException.class);
        assertThat(countOf(MEMBER_ID)).isZero();
    }

    @Test
    @DisplayName("호출 횟수는 회원마다 따로 센다")
    void quotaIsCountedPerMember() {
        // given
        exhaustQuota(MEMBER_ID);
        Trip trip = saveTrip(OTHER_MEMBER_ID, "일본");
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem item = BagItem.create(trip.getId(), OTHER_MEMBER_ID, "여권", "기타", 1, cabin.getId());
        item.update(null, null, null, true, null);
        bagItemRepository.save(item);
        given(aviationRegulationClient.judge(any(), any())).willReturn(List.of());

        // when
        securityCheckService.check(trip.getId(), OTHER_MEMBER_ID);

        // then
        assertThat(countOf(OTHER_MEMBER_ID)).isEqualTo(1);
    }

    /** 상한(20)까지 채운다. 조건부 UPDATE라 21번째부터는 더 올라가지 않는다. */
    private void exhaustQuota(Long memberId) {
        for (int i = 0; i < 20; i++) {
            memberRepository.consumeSecurityCheckQuota(memberId, 20);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private int countOf(Long memberId) {
        entityManager.flush();
        entityManager.clear();
        return memberRepository.findById(memberId).orElseThrow().getSecurityCheckCount();
    }

    private String riskLevelOf(SecurityCheckResponse response, Long itemId) {
        return itemResponseOf(response, itemId).getRiskLevel();
    }

    private String guideOf(SecurityCheckResponse response, Long itemId) {
        return itemResponseOf(response, itemId).getGuide();
    }

    private SecurityCheckItemResponse itemResponseOf(SecurityCheckResponse response, Long itemId) {
        return response.getItems().stream()
                .filter(item -> item.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("응답에 itemId=" + itemId + " 물품이 없다"));
    }

    private Trip saveTrip(Long memberId, String country) {
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

    private Bag saveBag(Trip trip, String name, CarriageType carriageType) {
        Bag bag = Bag.create(trip.getId(), MEMBER_ID, name);
        bag.changeCarriageType(carriageType);
        return bagRepository.save(bag);
    }

    /** 검사 대상은 가방에 담은 물품이므로 테스트 물품도 담은 상태로 만든다. */
    private BagItem saveItem(Trip trip, Bag bag, String name) {
        BagItem item = saveUnpackedItem(trip, bag, name);
        item.update(null, null, null, true, null);
        return bagItemRepository.save(item);
    }

    /** 체크리스트에만 있고 아직 안 담은 물품 — 검사 대상에서 빠지는지 볼 때 쓴다. */
    private BagItem saveUnpackedItem(Trip trip, Bag bag, String name) {
        return bagItemRepository.save(
                BagItem.create(trip.getId(), MEMBER_ID, name, "기타", 1, bag.getId()));
    }
}
