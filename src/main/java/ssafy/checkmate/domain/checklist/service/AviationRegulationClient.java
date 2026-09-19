package ssafy.checkmate.domain.checklist.service;

import java.util.List;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;

/**
 * 항공 보안 규정 지식베이스에 물품 반입 가능 여부를 묻는 어댑터.
 *
 * 서비스는 이 인터페이스에만 의존한다 — 실제 구현은 Bedrock Knowledge Base를 호출하므로
 * 테스트에서는 통째로 모킹한다.
 */
public interface AviationRegulationClient {

    /**
     * 묶음 목록을 한 번에 물어보고 판정을 돌려준다.
     *
     * 응답에 일부 묶음이 빠질 수 있다(모델이 문제없는 물품을 생략하는 경향). 그 처리는
     * 호출하는 쪽의 책임이다.
     */
    List<SecurityVerdict> judge(CountryJurisdiction jurisdiction, List<SecurityCheckTarget> targets);
}
