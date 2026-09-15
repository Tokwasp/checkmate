package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class BagItemAccessDeniedException extends BusinessException {

    public BagItemAccessDeniedException(String message) {
        super(BagItemErrorCode.BAG_ITEM_ACCESS_DENIED, message);
    }
}
