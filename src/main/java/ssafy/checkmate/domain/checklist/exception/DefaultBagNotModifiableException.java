package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class DefaultBagNotModifiableException extends BusinessException {

    public DefaultBagNotModifiableException() {
        super(BagErrorCode.DEFAULT_BAG_NOT_MODIFIABLE);
    }
}
