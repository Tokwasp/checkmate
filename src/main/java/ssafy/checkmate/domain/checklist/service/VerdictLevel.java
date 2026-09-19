package ssafy.checkmate.domain.checklist.service;

import java.util.Optional;
import ssafy.checkmate.domain.checklist.entity.RiskLevelType;

/**
 * 반입 규정 검사(30번)에서 모델이 물품마다 돌려주는 판정.
 *
 * {@link RiskLevelType}과 달리 "통과"({@link #NONE})를 포함한다 — 통과는 위험도 마스터에
 * 행이 없고 "행을 두지 않는 것"으로 표현되기 때문에 별도 값이 필요하다.
 */
public enum VerdictLevel {

    NONE(null, "통과"),
    WARNING(RiskLevelType.WARNING, "주의"),
    VIOLATION(RiskLevelType.VIOLATION, "금지");

    private final RiskLevelType riskLevelType;
    private final String label;

    VerdictLevel(RiskLevelType riskLevelType, String label) {
        this.riskLevelType = riskLevelType;
        this.label = label;
    }

    public static Optional<VerdictLevel> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (VerdictLevel level : values()) {
            if (level.name().equalsIgnoreCase(value.trim())) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    /** 통과면 비어 있다 — 위험도 행을 두지 않는다는 뜻. */
    public Optional<RiskLevelType> riskLevelType() {
        return Optional.ofNullable(riskLevelType);
    }

    public String getLabel() {
        return label;
    }

    /** 응답 정렬 기준 — 금지 → 주의 → 통과. */
    public int displayOrder() {
        return switch (this) {
            case VIOLATION -> 0;
            case WARNING -> 1;
            case NONE -> 2;
        };
    }
}
