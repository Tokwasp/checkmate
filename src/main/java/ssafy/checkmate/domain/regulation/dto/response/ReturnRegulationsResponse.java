package ssafy.checkmate.domain.regulation.dto.response;

import java.util.List;
import lombok.Getter;
import ssafy.checkmate.domain.regulation.entity.RegulationPart;
import ssafy.checkmate.domain.regulation.entity.ReturnRegulation;

/**
 * 귀국 규정 안내(34번) — 기내 반입과 한국 입국 검역 두 파트.
 *
 * 입력은 이미 표시 순서대로 정렬돼 있어야 한다. 파트만 나누고 순서는 건드리지 않는다.
 */
@Getter
public class ReturnRegulationsResponse {

    private final List<ReturnRegulationItemResponse> cabin;
    private final List<ReturnRegulationItemResponse> quarantine;

    private ReturnRegulationsResponse(List<ReturnRegulationItemResponse> cabin,
            List<ReturnRegulationItemResponse> quarantine) {
        this.cabin = cabin;
        this.quarantine = quarantine;
    }

    public static ReturnRegulationsResponse of(List<ReturnRegulation> regulations) {
        return new ReturnRegulationsResponse(
                itemsOf(regulations, RegulationPart.CABIN),
                itemsOf(regulations, RegulationPart.QUARANTINE));
    }

    private static List<ReturnRegulationItemResponse> itemsOf(List<ReturnRegulation> regulations,
            RegulationPart part) {
        return regulations.stream()
                .filter(regulation -> regulation.belongsTo(part))
                .map(ReturnRegulationItemResponse::of)
                .toList();
    }
}
