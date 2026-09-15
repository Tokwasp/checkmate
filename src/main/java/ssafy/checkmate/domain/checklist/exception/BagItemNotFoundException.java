package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class BagItemNotFoundException extends BusinessException {

    public BagItemNotFoundException() {
        super(BagItemErrorCode.BAG_ITEM_NOT_FOUND);
    }
}
