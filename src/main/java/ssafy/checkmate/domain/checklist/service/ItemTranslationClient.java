package ssafy.checkmate.domain.checklist.service;

import java.util.List;
import java.util.Map;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;

/**
 * 물품 이름을 그 나라 말 검색어로 옮긴다 — 반입 규정 검사(30번)의 규정 검색 앞단.
 *
 * 규정 문서가 현지어라 한국어 이름만으로는 임베딩이 언어를 건너 매칭하지 못해 규정을 통째로
 * 놓친다. 물품 마스터에 별칭을 미리 적어 두는 방법은 마스터에 있는 물품에만 닿는데, 체크리스트로
 * 직접 추가한 물품은 마스터에 없다 — 그래서 들어오는 이름을 그때그때 옮긴다.
 *
 * 구현을 갈아끼울 수 있게 인터페이스로 둔다(현재 구현은 {@link GmsItemTranslationClient}).
 */
public interface ItemTranslationClient {

    /**
     * 물품 이름 목록을 그 나라 언어의 검색어로 옮긴다.
     *
     * @return 한국어 이름 → 현지어 검색어. 옮기지 못한 이름은 결과에서 빠지고, 호출 자체가
     *         실패하면 빈 Map이다 — 번역이 없다고 검사를 멈추지는 않는다.
     */
    Map<String, String> translate(List<String> itemNames, CountryJurisdiction jurisdiction);
}
