package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class ItemNotRecognizedException extends BusinessException {

    public ItemNotRecognizedException() {
        super(QuarantineCheckErrorCode.ITEM_NOT_RECOGNIZED);
    }
}
