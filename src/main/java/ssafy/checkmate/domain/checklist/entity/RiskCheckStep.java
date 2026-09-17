package ssafy.checkmate.domain.checklist.entity;

public enum RiskCheckStep {
    ITEM_RECOGNITION("물품 인식"),
    SECURITY_CHECK("반입 검사");

    private final String label;

    RiskCheckStep(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
