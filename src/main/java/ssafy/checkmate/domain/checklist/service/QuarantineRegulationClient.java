package ssafy.checkmate.domain.checklist.service;

/**
 * 한국 세관·검역 규정 지식베이스에 물품 반입 가능 여부를 묻는 어댑터(35번 2·3단계).
 *
 * 항공 보안 규정을 묻는 {@link AviationRegulationClient}와 지식베이스는 같지만 검색 범위
 * ({@code scope=import}, {@code KR})와 판정 기준이 달라 인터페이스를 따로 둔다.
 */
public interface QuarantineRegulationClient {

    /** @throws RuntimeException 검색·판정 실패. 서비스가 500으로 바꾼다 */
    QuarantineVerdict judge(ItemAnalysis item);
}
