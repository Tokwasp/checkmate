package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class MixedBagItemsException extends BusinessException {

    public MixedBagItemsException() {
        super(BagItemErrorCode.MIXED_BAG_ITEMS);
    }
}
