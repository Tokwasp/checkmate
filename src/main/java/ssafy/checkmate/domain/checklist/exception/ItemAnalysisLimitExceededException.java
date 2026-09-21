package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class ItemAnalysisLimitExceededException extends BusinessException {

    public ItemAnalysisLimitExceededException() {
        super(QuarantineCheckErrorCode.ITEM_ANALYSIS_LIMIT_EXCEEDED);
    }
}
