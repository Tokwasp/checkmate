package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class BagAccessDeniedException extends BusinessException {

    public BagAccessDeniedException(String message) {
        super(BagErrorCode.BAG_ACCESS_DENIED, message);
    }
}
