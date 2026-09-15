package ssafy.checkmate.domain.trip.entity;

/**
 * 여행 진행 상태. 컬럼으로 저장하지 않는다 — endDate 기준으로 조회 시점마다 계산한다.
 */
public enum TripType {
    CURRENT,
    PAST
}
