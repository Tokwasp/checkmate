package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.service.ItemAnalysis;
import ssafy.checkmate.domain.checklist.service.QuarantineVerdict;

@Getter
public class QuarantineCheckResponse {

    private final QuarantineItemResponse item;
    private final QuarantineVerdictResponse verdict;

    private QuarantineCheckResponse(QuarantineItemResponse item, QuarantineVerdictResponse verdict) {
        this.item = item;
        this.verdict = verdict;
    }

    public static QuarantineCheckResponse of(ItemAnalysis item, QuarantineVerdict verdict) {
        return new QuarantineCheckResponse(QuarantineItemResponse.of(item), QuarantineVerdictResponse.of(verdict));
    }
}
