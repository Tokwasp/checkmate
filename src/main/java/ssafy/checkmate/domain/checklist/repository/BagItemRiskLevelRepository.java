package ssafy.checkmate.domain.checklist.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.checkmate.domain.checklist.entity.BagItemRiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskCheckStep;

public interface BagItemRiskLevelRepository extends JpaRepository<BagItemRiskLevel, Long> {

    @Query("""
            SELECT br.bagItemId AS bagItemId, rl.level AS level
            FROM BagItemRiskLevel br
            JOIN RiskLevel rl ON br.riskLevelId = rl.id
            WHERE br.bagItemId IN :bagItemIds AND br.step = :step
            """)
    List<BagItemRiskLevelView> findRiskLevelsByBagItemIdsAndStep(
            @Param("bagItemIds") List<Long> bagItemIds, @Param("step") RiskCheckStep step);
}
