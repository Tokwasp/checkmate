package ssafy.checkmate.domain.checklist.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RiskLevelType {
    WARNING("주의"),
    VIOLATION("금지");

    private final String label;

    RiskLevelType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
