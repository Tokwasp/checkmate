package ssafy.checkmate.domain.checklist.repository;

import ssafy.checkmate.domain.checklist.entity.RiskLevelType;

public interface BagItemRiskLevelView {

    Long getBagItemId();

    RiskLevelType getLevel();
}
