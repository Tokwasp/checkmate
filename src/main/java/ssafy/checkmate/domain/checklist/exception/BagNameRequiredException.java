package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class BagNameRequiredException extends BusinessException {

    public BagNameRequiredException() {
        super(BagErrorCode.BAG_NAME_REQUIRED);
    }
}
