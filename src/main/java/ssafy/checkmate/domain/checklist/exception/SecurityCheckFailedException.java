package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class SecurityCheckFailedException extends BusinessException {

    public SecurityCheckFailedException() {
        super(SecurityCheckErrorCode.SECURITY_CHECK_FAILED);
    }
}
