package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.BagItemRiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskCheckStep;
import ssafy.checkmate.domain.checklist.entity.RiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskLevelType;
import ssafy.checkmate.domain.checklist.repository.BagItemRiskLevelRepository;
import ssafy.checkmate.domain.checklist.repository.RiskLevelRepository;

/**
 * 반입 규정 검사(30번)가 물품 인식(26번)의 위험도를 덮어쓰지 않도록, 단계를 구분하는
 * 조회·삭제 메서드가 실제로 단계를 가리는지 확인한다.
 *
 * 기존 {@code findByBagItemId}/{@code deleteByBagItemId}는 단계를 안 가린다 — 그대로 쓰면
 * 한 단계의 저장이 다른 단계의 값을 지운다. 이 테스트가 그 회귀를 막는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BagItemRiskLevelRepositoryTest {

    private static final Long BAG_ITEM_ID = 100L;

    @Autowired
    private BagItemRiskLevelRepository bagItemRiskLevelRepository;

    @Autowired
    private RiskLevelRepository riskLevelRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("같은 물품에 단계가 다른 위험도가 함께 있으면 요청한 단계의 행만 찾는다")
    void findsOnlyRequestedStep() {
        // given
        Long warningId = riskLevelIdOf(RiskLevelType.WARNING);
        Long violationId = riskLevelIdOf(RiskLevelType.VIOLATION);
        bagItemRiskLevelRepository.save(
                BagItemRiskLevel.create(BAG_ITEM_ID, warningId, RiskCheckStep.ITEM_RECOGNITION));
        bagItemRiskLevelRepository.save(
                BagItemRiskLevel.create(BAG_ITEM_ID, violationId, RiskCheckStep.SECURITY_CHECK));
        flushAndClear();

        // when
        BagItemRiskLevel recognition = bagItemRiskLevelRepository
                .findByBagItemIdAndStep(BAG_ITEM_ID, RiskCheckStep.ITEM_RECOGNITION).orElseThrow();
        BagItemRiskLevel securityCheck = bagItemRiskLevelRepository
                .findByBagItemIdAndStep(BAG_ITEM_ID, RiskCheckStep.SECURITY_CHECK).orElseThrow();

        // then
        assertThat(recognition.getRiskLevelId()).isEqualTo(warningId);
        assertThat(securityCheck.getRiskLevelId()).isEqualTo(violationId);
    }

    @Test
    @DisplayName("단계를 지정해 지우면 다른 단계의 위험도는 남는다")
    void deletesOnlyRequestedStep() {
        // given
        bagItemRiskLevelRepository.save(BagItemRiskLevel.create(
                BAG_ITEM_ID, riskLevelIdOf(RiskLevelType.WARNING), RiskCheckStep.ITEM_RECOGNITION));
        bagItemRiskLevelRepository.save(BagItemRiskLevel.create(
                BAG_ITEM_ID, riskLevelIdOf(RiskLevelType.VIOLATION), RiskCheckStep.SECURITY_CHECK));
        flushAndClear();

        // when
        bagItemRiskLevelRepository.deleteByBagItemIdAndStep(BAG_ITEM_ID, RiskCheckStep.SECURITY_CHECK);
        flushAndClear();

        // then
        assertThat(bagItemRiskLevelRepository.findByBagItemIdAndStep(BAG_ITEM_ID, RiskCheckStep.SECURITY_CHECK))
                .isEmpty();
        assertThat(bagItemRiskLevelRepository.findByBagItemIdAndStep(BAG_ITEM_ID, RiskCheckStep.ITEM_RECOGNITION))
                .isPresent();
    }

    @Test
    @DisplayName("해당 단계에 행이 없으면 빈 값을 돌려준다")
    void returnsEmptyWhenStepAbsent() {
        // given
        bagItemRiskLevelRepository.save(BagItemRiskLevel.create(
                BAG_ITEM_ID, riskLevelIdOf(RiskLevelType.WARNING), RiskCheckStep.ITEM_RECOGNITION));
        flushAndClear();

        // when & then
        assertThat(bagItemRiskLevelRepository.findByBagItemIdAndStep(BAG_ITEM_ID, RiskCheckStep.SECURITY_CHECK))
                .isEmpty();
    }

    private Long riskLevelIdOf(RiskLevelType type) {
        return riskLevelRepository.findByLevel(type).map(RiskLevel::getId).orElseThrow();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
