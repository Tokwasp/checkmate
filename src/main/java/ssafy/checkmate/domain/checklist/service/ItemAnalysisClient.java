package ssafy.checkmate.domain.checklist.service;

import java.util.Optional;

/**
 * 사진에서 물품의 품목·분류·원재료·상태를 읽어 내는 어댑터(35번 1단계).
 *
 * 서비스는 이 인터페이스에만 의존한다 — 실제 구현은 GMS(Opus 비전)를 호출하므로 테스트에서는
 * 통째로 모킹한다.
 */
public interface ItemAnalysisClient {

    /**
     * @return 사진에서 판정할 물품을 찾았으면 그 정보, 못 찾았으면(사람·풍경·빈 화면 등) 빈 값
     * @throws RuntimeException 호출 실패·빈 응답·해석할 수 없는 응답. 서비스가 500으로 바꾼다
     */
    Optional<ItemAnalysis> analyze(ScanImage image);
}
