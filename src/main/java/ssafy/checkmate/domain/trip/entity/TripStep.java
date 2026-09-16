package ssafy.checkmate.domain.trip.entity;

/**
 * 여행 준비 진행 단계. 여행 생성 시 {@link #INFO_INPUT}으로 시작해 순서대로 진행된다.
 */
public enum TripStep {
    INFO_INPUT(1, "여행 정보 입력"),
    CHECKLIST_CREATION(2, "체크리스트 생성"),
    ITEM_RECOGNITION(3, "물건 인식"),
    PRE_RETURN_CHECK(4, "귀가 전 점검");

    private final int order;
    private final String displayName;

    TripStep(int order, String displayName) {
        this.order = order;
        this.displayName = displayName;
    }

    public int getOrder() {
        return order;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static int totalStepCount() {
        return values().length;
    }
}
