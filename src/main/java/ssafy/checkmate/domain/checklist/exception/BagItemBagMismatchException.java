package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class BagItemBagMismatchException extends BusinessException {

    public BagItemBagMismatchException() {
        super(BagItemErrorCode.BAG_ITEM_BAG_MISMATCH);
    }
}
