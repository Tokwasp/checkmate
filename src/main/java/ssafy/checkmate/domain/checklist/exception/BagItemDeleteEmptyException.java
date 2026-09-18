package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class BagItemDeleteEmptyException extends BusinessException {

    public BagItemDeleteEmptyException() {
        super(BagItemErrorCode.BAG_ITEM_DELETE_EMPTY);
    }
}
