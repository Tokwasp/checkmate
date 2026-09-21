package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class ItemAnalysisFailedException extends BusinessException {

    public ItemAnalysisFailedException() {
        super(QuarantineCheckErrorCode.ITEM_ANALYSIS_FAILED);
    }
}
