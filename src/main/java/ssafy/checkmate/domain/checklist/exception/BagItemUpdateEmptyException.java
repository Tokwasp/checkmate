package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class BagItemUpdateEmptyException extends BusinessException {

    public BagItemUpdateEmptyException() {
        super(BagItemErrorCode.BAG_ITEM_UPDATE_EMPTY);
    }
}
