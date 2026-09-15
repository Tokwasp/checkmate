package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class BagNotFoundException extends BusinessException {

    public BagNotFoundException() {
        super(BagErrorCode.BAG_NOT_FOUND);
    }
}
