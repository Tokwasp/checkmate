package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class SecurityCheckLimitExceededException extends BusinessException {

    public SecurityCheckLimitExceededException() {
        super(SecurityCheckErrorCode.SECURITY_CHECK_LIMIT_EXCEEDED);
    }
}
