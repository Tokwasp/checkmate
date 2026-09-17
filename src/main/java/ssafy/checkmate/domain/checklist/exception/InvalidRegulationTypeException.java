package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class InvalidRegulationTypeException extends BusinessException {

    public InvalidRegulationTypeException() {
        super(ScanErrorCode.INVALID_REGULATION_TYPE);
    }
}
