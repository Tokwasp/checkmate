package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class DefaultBagNotDeletableException extends BusinessException {

    public DefaultBagNotDeletableException() {
        super(BagErrorCode.DEFAULT_BAG_NOT_DELETABLE);
    }
}
