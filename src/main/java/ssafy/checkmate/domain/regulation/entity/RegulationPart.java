package ssafy.checkmate.domain.regulation.entity;

/**
 * 귀국 규정 안내(34번)의 두 파트. 응답에서 {@code cabin[]}과 {@code quarantine[]}로 갈린다.
 */
public enum RegulationPart {
    /** 기내 반입 — 출발 공항 보안검색 기준 (항공보안법 시행규칙 별표 1). */
    CABIN,
    /** 한국 입국 검역 — 입국 시 세관·검역 기준. */
    QUARANTINE
}
