package ssafy.checkmate.domain.checklist.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RiskLevelType {
    FORBIDDEN("금지"),
    CAUTION("주의");

    private final String label;

    RiskLevelType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
