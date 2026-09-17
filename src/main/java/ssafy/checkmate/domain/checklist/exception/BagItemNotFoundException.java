package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class BagItemNotFoundException extends BusinessException {

    public BagItemNotFoundException() {
        super(BagItemErrorCode.BAG_ITEM_NOT_FOUND);
    }

    /** 인벤토리 화면(26~28번)에서는 "체크리스트 항목" 대신 "물품"으로 안내한다. */
    public BagItemNotFoundException(String message) {
        super(BagItemErrorCode.BAG_ITEM_NOT_FOUND, message);
    }
}
