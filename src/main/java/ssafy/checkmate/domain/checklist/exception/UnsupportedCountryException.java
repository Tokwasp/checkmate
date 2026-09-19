package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class UnsupportedCountryException extends BusinessException {

    public UnsupportedCountryException() {
        super(SecurityCheckErrorCode.UNSUPPORTED_COUNTRY);
    }
}
