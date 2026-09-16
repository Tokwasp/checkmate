package ssafy.checkmate.domain.trip.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 여행 진행 상태. 컬럼으로 저장하지 않는다 — 조회 시점마다 오늘 날짜 기준으로 계산한다.
 */
public enum TripType {
    ONGOING("여행 중"),
    UPCOMING("여행 예정"),
    PAST("지난 여행");

    private final String label;

    TripType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
