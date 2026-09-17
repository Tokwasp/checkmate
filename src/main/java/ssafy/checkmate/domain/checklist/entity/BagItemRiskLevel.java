package ssafy.checkmate.domain.checklist.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.global.entity.BaseEntity;

/**
 * {@link BagItem}과 {@link RiskLevel}의 다대다를 단계({@link RiskCheckStep})별로 풀어낸 중간 테이블.
 * 간접 참조만 가진다(FK 제약/연관관계 없음) — {@link BagItem}과 동일한 스타일.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BagItemRiskLevel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long bagItemId;

    private Long riskLevelId;

    @Enumerated(EnumType.STRING)
    private RiskCheckStep step;

    @Builder(access = AccessLevel.PRIVATE)
    private BagItemRiskLevel(Long bagItemId, Long riskLevelId, RiskCheckStep step) {
        this.bagItemId = bagItemId;
        this.riskLevelId = riskLevelId;
        this.step = step;
    }

    public static BagItemRiskLevel create(Long bagItemId, Long riskLevelId, RiskCheckStep step) {
        return BagItemRiskLevel.builder()
                .bagItemId(bagItemId)
                .riskLevelId(riskLevelId)
                .step(step)
                .build();
    }

    public void changeRiskLevel(Long riskLevelId) {
        this.riskLevelId = riskLevelId;
    }
}
