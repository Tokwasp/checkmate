package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.service.QuarantineVerdict;
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;

@Getter
public class QuarantineVerdictResponse {

    /** 금지 · 제한 · 허용 */
    private final RegulationLevel level;
    private final String reason;
    /** 근거 문서에 금액이 없거나 허용이면 null. */
    private final String penalty;
    /** 허용이면 null. */
    private final String action;

    private QuarantineVerdictResponse(RegulationLevel level, String reason, String penalty, String action) {
        this.level = level;
        this.reason = reason;
        this.penalty = penalty;
        this.action = action;
    }

    public static QuarantineVerdictResponse of(QuarantineVerdict verdict) {
        return new QuarantineVerdictResponse(
                verdict.level(), verdict.reason(), verdict.penalty(), verdict.action());
    }
}
