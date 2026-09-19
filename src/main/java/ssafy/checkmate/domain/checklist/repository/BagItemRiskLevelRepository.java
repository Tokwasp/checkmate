package ssafy.checkmate.domain.checklist.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.checkmate.domain.checklist.entity.BagItemRiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskCheckStep;

public interface BagItemRiskLevelRepository extends JpaRepository<BagItemRiskLevel, Long> {

    Optional<BagItemRiskLevel> findByBagItemId(Long bagItemId);

    void deleteByBagItemId(Long bagItemId);

    /**
     * 단계를 구분해서 찾는다. 위의 {@link #findByBagItemId}는 단계를 안 가려서, 반입 규정
     * 검사(30번)가 그대로 쓰면 물품 인식(26번)이 저장한 {@code ITEM_RECOGNITION} 행을
     * 덮어쓴다. 26번이 기존 메서드를 쓰고 있으므로 고치지 않고 단계별 메서드를 따로 둔다.
     */
    Optional<BagItemRiskLevel> findByBagItemIdAndStep(Long bagItemId, RiskCheckStep step);

    /** 단계를 구분해서 지운다 — 이유는 {@link #findByBagItemIdAndStep}와 같다. */
    void deleteByBagItemIdAndStep(Long bagItemId, RiskCheckStep step);

    void deleteAllByBagItemIdIn(List<Long> bagItemIds);

    @Query("""
            SELECT br.bagItemId AS bagItemId, rl.level AS level
            FROM BagItemRiskLevel br
            JOIN RiskLevel rl ON br.riskLevelId = rl.id
            WHERE br.bagItemId IN :bagItemIds AND br.step = :step
            """)
    List<BagItemRiskLevelView> findRiskLevelsByBagItemIdsAndStep(
            @Param("bagItemIds") List<Long> bagItemIds, @Param("step") RiskCheckStep step);
}
