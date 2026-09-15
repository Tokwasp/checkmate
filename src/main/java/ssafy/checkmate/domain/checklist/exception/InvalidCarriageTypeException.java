package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class InvalidCarriageTypeException extends BusinessException {

    public InvalidCarriageTypeException() {
        super(BagErrorCode.INVALID_CARRIAGE_TYPE);
    }
}
