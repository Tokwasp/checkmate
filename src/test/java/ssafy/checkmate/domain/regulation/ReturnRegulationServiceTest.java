package ssafy.checkmate.domain.regulation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.regulation.dto.response.ReturnRegulationsResponse;
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;
import ssafy.checkmate.domain.regulation.entity.RegulationPart;
import ssafy.checkmate.domain.regulation.entity.ReturnRegulation;
import ssafy.checkmate.domain.regulation.repository.ReturnRegulationRepository;
import ssafy.checkmate.domain.regulation.service.ReturnRegulationService;

/**
 * 시드가 채워 둔 10행은 지우고 테스트가 직접 넣은 행으로 검증한다 — 정렬·파트 분리 규칙이
 * 시드 내용이 아니라 서비스 로직에서 나오는지 보려는 것이다. 클래스 전체가 롤백되므로 시드는
 * 다른 테스트에 영향이 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReturnRegulationServiceTest {

    @Autowired
    private ReturnRegulationService returnRegulationService;

    @Autowired
    private ReturnRegulationRepository returnRegulationRepository;

    @BeforeEach
    void clearSeed() {
        returnRegulationRepository.deleteAll();
    }

    @Test
    @DisplayName("규정을 파트별로 나눠 기내 반입과 한국 입국 검역 두 목록으로 내려준다")
    void splitsByPart() {
        // given
        returnRegulationRepository.save(regulation(RegulationPart.CABIN, 1, "칼", RegulationLevel.PROHIBITED));
        returnRegulationRepository.save(
                regulation(RegulationPart.QUARANTINE, 1, "생과일·채소·식물·흙", RegulationLevel.PROHIBITED));
        returnRegulationRepository.save(
                regulation(RegulationPart.CABIN, 2, "고도주", RegulationLevel.RESTRICTED));

        // when
        ReturnRegulationsResponse response = returnRegulationService.getReturnRegulations();

        // then
        assertThat(response.getCabin()).extracting("name").containsExactly("칼", "고도주");
        assertThat(response.getQuarantine()).extracting("name").containsExactly("생과일·채소·식물·흙");
    }

    @Test
    @DisplayName("각 파트 안에서는 sortOrder 오름차순으로 내려준다 — 저장된 순서와 무관하다")
    void sortsBySortOrderWithinPart() {
        // given: 일부러 순서를 뒤집어 저장한다
        returnRegulationRepository.save(regulation(RegulationPart.CABIN, 3, "가위", RegulationLevel.PROHIBITED));
        returnRegulationRepository.save(
                regulation(RegulationPart.CABIN, 1, "보조배터리", RegulationLevel.ALLOWED));
        returnRegulationRepository.save(regulation(RegulationPart.CABIN, 2, "칼", RegulationLevel.PROHIBITED));
        returnRegulationRepository.save(
                regulation(RegulationPart.QUARANTINE, 2, "주류·담배·향수", RegulationLevel.RESTRICTED));
        returnRegulationRepository.save(
                regulation(RegulationPart.QUARANTINE, 1, "의약품·건강기능식품", RegulationLevel.RESTRICTED));

        // when
        ReturnRegulationsResponse response = returnRegulationService.getReturnRegulations();

        // then
        assertThat(response.getCabin()).extracting("name").containsExactly("보조배터리", "칼", "가위");
        assertThat(response.getQuarantine()).extracting("name")
                .containsExactly("의약품·건강기능식품", "주류·담배·향수");
    }

    @Test
    @DisplayName("항목마다 이름·판정·안내 문구를 그대로 내려준다")
    void mapsItemFields() {
        // given
        returnRegulationRepository.save(ReturnRegulation.of(RegulationPart.CABIN, 1, "보조배터리",
                RegulationLevel.ALLOWED, "160Wh 이하만 1인 2개까지 기내 반입 가능합니다."));

        // when
        ReturnRegulationsResponse response = returnRegulationService.getReturnRegulations();

        // then
        assertThat(response.getCabin()).hasSize(1)
                .first()
                .extracting("name", "level", "guide")
                .containsExactly("보조배터리", RegulationLevel.ALLOWED, "160Wh 이하만 1인 2개까지 기내 반입 가능합니다.");
    }

    @Test
    @DisplayName("저장된 규정이 없으면 에러 없이 두 목록을 비워 내려준다")
    void returnsEmptyListsWhenNoRegulations() {
        // when
        ReturnRegulationsResponse response = returnRegulationService.getReturnRegulations();

        // then
        assertThat(response.getCabin()).isEmpty();
        assertThat(response.getQuarantine()).isEmpty();
    }

    private ReturnRegulation regulation(RegulationPart part, int sortOrder, String name, RegulationLevel level) {
        return ReturnRegulation.of(part, sortOrder, name, level, name + " 안내");
    }
}
