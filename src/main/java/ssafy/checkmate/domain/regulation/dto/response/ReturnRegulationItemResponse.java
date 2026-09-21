package ssafy.checkmate.domain.regulation.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;
import ssafy.checkmate.domain.regulation.entity.ReturnRegulation;

/**
 * 귀국 규정 한 줄. 화면이 그리는 세 값만 내린다 — 파트와 순서는 응답 구조(cabin·quarantine와
 * 배열 순서)가 이미 말해 주므로 따로 싣지 않는다.
 */
@Getter
public class ReturnRegulationItemResponse {

    private final String name;
    private final RegulationLevel level;
    private final String guide;

    private ReturnRegulationItemResponse(String name, RegulationLevel level, String guide) {
        this.name = name;
        this.level = level;
        this.guide = guide;
    }

    public static ReturnRegulationItemResponse of(ReturnRegulation regulation) {
        return new ReturnRegulationItemResponse(regulation.getName(), regulation.getLevel(), regulation.getGuide());
    }
}
