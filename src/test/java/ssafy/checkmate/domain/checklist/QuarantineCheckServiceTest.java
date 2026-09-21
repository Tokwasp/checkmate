package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.request.QuarantineCheckRequest;
import ssafy.checkmate.domain.checklist.dto.response.QuarantineCheckResponse;
import ssafy.checkmate.domain.checklist.exception.InvalidScanImageException;
import ssafy.checkmate.domain.checklist.exception.ItemAnalysisFailedException;
import ssafy.checkmate.domain.checklist.exception.ItemAnalysisLimitExceededException;
import ssafy.checkmate.domain.checklist.exception.ItemNotRecognizedException;
import ssafy.checkmate.domain.checklist.exception.QuarantineCheckFailedException;
import ssafy.checkmate.domain.checklist.exception.UnsupportedScanImageException;
import ssafy.checkmate.domain.checklist.service.ItemAnalysis;
import ssafy.checkmate.domain.checklist.service.ItemAnalysisClient;
import ssafy.checkmate.domain.checklist.service.QuarantineCheckService;
import ssafy.checkmate.domain.checklist.service.QuarantineRegulationClient;
import ssafy.checkmate.domain.checklist.service.QuarantineVerdict;
import ssafy.checkmate.domain.checklist.service.ScanImage;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;

/**
 * 촬영 물품 반입 판정(35번) 서비스 테스트.
 *
 * GMS(Opus)와 Bedrock은 실제로 부르지 않고 {@link ItemAnalysisClient}·{@link QuarantineRegulationClient}를
 * 모킹한다 — 실호출은 돈이 나가고 CI에 자격증명이 없다. 호출 횟수는 회원 행에 기록되므로 DB는 진짜로 쓴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuarantineCheckServiceTest {

    private static final ItemAnalysis JERKY =
            new ItemAnalysis("육포", "육류 가공품", List.of("돼지고기", "소고기"), "건조 가공");
    private static final QuarantineVerdict PROHIBITED = QuarantineVerdict.of(RegulationLevel.PROHIBITED,
            "육류 가공품은 축산물 검역 대상이라 휴대 반입이 불가해요.",
            "적발 시 위반 횟수·원산지에 따라 100만~1,000만 원의 과태료가 부과될 수 있어요.",
            "소지하고 있다면 공항 검역대에 자진 신고하세요.");

    private Long memberId;

    @Autowired
    private QuarantineCheckService quarantineCheckService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

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
    @DisplayName("사진을 판정하면 읽어 낸 물품과 판정(사유·과태료·조치)을 돌려준다")
    void check() {
        // given
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.of(JERKY));
        given(quarantineRegulationClient.judge(JERKY)).willReturn(PROHIBITED);

        // when
        QuarantineCheckResponse response = quarantineCheckService.check(memberId, request("image/jpeg", 3));

        // then
        assertThat(response.getItem().getName()).isEqualTo("육포");
        assertThat(response.getItem().getCategory()).isEqualTo("육류 가공품");
        assertThat(response.getItem().getIngredients()).containsExactly("돼지고기", "소고기");
        assertThat(response.getItem().getState()).isEqualTo("건조 가공");
        assertThat(response.getVerdict().getLevel()).isEqualTo(RegulationLevel.PROHIBITED);
        assertThat(response.getVerdict().getReason()).isEqualTo("육류 가공품은 축산물 검역 대상이라 휴대 반입이 불가해요.");
        assertThat(response.getVerdict().getPenalty()).contains("100만~1,000만 원");
        assertThat(response.getVerdict().getAction()).isEqualTo("소지하고 있다면 공항 검역대에 자진 신고하세요.");
    }

    @Test
    @DisplayName("올린 사진의 형식과 바이트를 그대로 분석 클라이언트에 넘긴다")
    void checkPassesImageToAnalysisClient() {
        // given
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.of(JERKY));
        given(quarantineRegulationClient.judge(JERKY)).willReturn(PROHIBITED);

        // when
        quarantineCheckService.check(memberId, request("image/png", 4));

        // then
        ArgumentCaptor<ScanImage> captor = ArgumentCaptor.forClass(ScanImage.class);
        verify(itemAnalysisClient).analyze(captor.capture());
        assertThat(captor.getValue().mimeType()).isEqualTo("image/png");
        assertThat(captor.getValue().bytes()).hasSize(4);
    }

    @Test
    @DisplayName("성공하면 호출 횟수를 하나 소모하고, 30번(반입 규정 검사) 횟수는 건드리지 않는다")
    void checkConsumesOnlyItemAnalysisQuota() {
        // given
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.of(JERKY));
        given(quarantineRegulationClient.judge(JERKY)).willReturn(PROHIBITED);

        // when
        quarantineCheckService.check(memberId, request("image/jpeg", 3));

        // then
        assertThat(itemAnalysisCountOf(memberId)).isEqualTo(1);
        assertThat(securityCheckCountOf(memberId)).isZero();
    }

    @Test
    @DisplayName("사진에서 물품을 찾지 못하면 422 예외가 발생하지만 소모한 횟수는 그대로다")
    void checkWhenItemNotRecognized() {
        // given
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.empty());

        // when // then
        assertThatThrownBy(() -> quarantineCheckService.check(memberId, request("image/jpeg", 3)))
                .isInstanceOf(ItemNotRecognizedException.class)
                .hasMessage("물품을 인식하지 못했습니다.");
        assertThat(itemAnalysisCountOf(memberId)).isEqualTo(1);
        verify(quarantineRegulationClient, never()).judge(any());
    }

    @Test
    @DisplayName("물품 분석 호출이 실패하면 500 예외가 발생하지만 소모한 횟수는 그대로다")
    void checkWhenAnalysisFails() {
        // given
        given(itemAnalysisClient.analyze(any())).willThrow(new RuntimeException("GMS 호출 실패"));

        // when // then
        assertThatThrownBy(() -> quarantineCheckService.check(memberId, request("image/jpeg", 3)))
                .isInstanceOf(ItemAnalysisFailedException.class)
                .hasMessage("물품 이미지 분석에 실패했습니다.");
        assertThat(itemAnalysisCountOf(memberId)).isEqualTo(1);
        verify(quarantineRegulationClient, never()).judge(any());
    }

    @Test
    @DisplayName("규정 검색·판정이 실패하면 500 예외가 발생하지만 소모한 횟수는 그대로다")
    void checkWhenRegulationFails() {
        // given
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.of(JERKY));
        given(quarantineRegulationClient.judge(any())).willThrow(new RuntimeException("Bedrock 호출 실패"));

        // when // then
        assertThatThrownBy(() -> quarantineCheckService.check(memberId, request("image/jpeg", 3)))
                .isInstanceOf(QuarantineCheckFailedException.class)
                .hasMessage("규정 검사에 실패했습니다.");
        assertThat(itemAnalysisCountOf(memberId)).isEqualTo(1);
    }

    @Test
    @DisplayName("호출 횟수를 모두 썼으면 429 예외가 발생하고 GMS·Bedrock을 부르지 않는다")
    void checkWhenQuotaExhausted() {
        // given
        setItemAnalysisCount(memberId, 20);

        // when // then
        assertThatThrownBy(() -> quarantineCheckService.check(memberId, request("image/jpeg", 3)))
                .isInstanceOf(ItemAnalysisLimitExceededException.class)
                .hasMessage("물품 분석 횟수를 모두 사용했습니다.");
        assertThat(itemAnalysisCountOf(memberId)).isEqualTo(20);
        verify(itemAnalysisClient, never()).analyze(any());
        verify(quarantineRegulationClient, never()).judge(any());
    }

    @Test
    @DisplayName("마지막 한 번 남았을 때는 통과하고 그 다음부터 막힌다")
    void checkAtQuotaBoundary() {
        // given
        setItemAnalysisCount(memberId, 19);
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.of(JERKY));
        given(quarantineRegulationClient.judge(JERKY)).willReturn(PROHIBITED);

        // when
        quarantineCheckService.check(memberId, request("image/jpeg", 3));

        // then
        assertThat(itemAnalysisCountOf(memberId)).isEqualTo(20);
        assertThatThrownBy(() -> quarantineCheckService.check(memberId, request("image/jpeg", 3)))
                .isInstanceOf(ItemAnalysisLimitExceededException.class);
    }

    @Test
    @DisplayName("30번 횟수를 다 써도 물품 분석은 막히지 않는다")
    void checkWhenSecurityCheckQuotaExhausted() {
        // given
        setSecurityCheckCount(memberId, 20);
        given(itemAnalysisClient.analyze(any())).willReturn(Optional.of(JERKY));
        given(quarantineRegulationClient.judge(JERKY)).willReturn(PROHIBITED);

        // when
        QuarantineCheckResponse response = quarantineCheckService.check(memberId, request("image/jpeg", 3));

        // then
        assertThat(response.getVerdict().getLevel()).isEqualTo(RegulationLevel.PROHIBITED);
    }

    @Test
    @DisplayName("지원하지 않는 형식이면 400 예외가 발생하고 횟수를 소모하지 않는다")
    void checkWithUnsupportedImage() {
        // when // then
        assertThatThrownBy(() -> quarantineCheckService.check(memberId, request("image/gif", 3)))
                .isInstanceOf(UnsupportedScanImageException.class);
        assertThat(itemAnalysisCountOf(memberId)).isZero();
        verify(itemAnalysisClient, never()).analyze(any());
    }

    @Test
    @DisplayName("빈 이미지면 400 예외가 발생하고 횟수를 소모하지 않는다")
    void checkWithEmptyImage() {
        // given
        QuarantineCheckRequest request = new QuarantineCheckRequest();
        request.setImage(new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[0]));

        // when // then
        assertThatThrownBy(() -> quarantineCheckService.check(memberId, request))
                .isInstanceOf(InvalidScanImageException.class);
        assertThat(itemAnalysisCountOf(memberId)).isZero();
        verify(itemAnalysisClient, never()).analyze(any());
    }

    private QuarantineCheckRequest request(String contentType, int size) {
        QuarantineCheckRequest request = new QuarantineCheckRequest();
        request.setImage(new MockMultipartFile("image", "photo", contentType, new byte[size]));
        return request;
    }

    private int itemAnalysisCountOf(Long id) {
        return entityManager.createQuery(
                "select m.itemAnalysisCount from Member m where m.id = :id", Integer.class)
                .setParameter("id", id).getSingleResult();
    }

    private int securityCheckCountOf(Long id) {
        return entityManager.createQuery(
                "select m.securityCheckCount from Member m where m.id = :id", Integer.class)
                .setParameter("id", id).getSingleResult();
    }

    private void setItemAnalysisCount(Long id, int count) {
        entityManager.createQuery("update Member m set m.itemAnalysisCount = :count where m.id = :id")
                .setParameter("count", count).setParameter("id", id).executeUpdate();
    }

    private void setSecurityCheckCount(Long id, int count) {
        entityManager.createQuery("update Member m set m.securityCheckCount = :count where m.id = :id")
                .setParameter("count", count).setParameter("id", id).executeUpdate();
    }
}
