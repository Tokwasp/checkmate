package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class NoItemsToCheckException extends BusinessException {

    public NoItemsToCheckException() {
        super(SecurityCheckErrorCode.NO_ITEMS_TO_CHECK);
    }
}
