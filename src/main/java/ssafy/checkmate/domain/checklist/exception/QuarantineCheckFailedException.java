package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class QuarantineCheckFailedException extends BusinessException {

    public QuarantineCheckFailedException() {
        super(QuarantineCheckErrorCode.QUARANTINE_CHECK_FAILED);
    }
}
