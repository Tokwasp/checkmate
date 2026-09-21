package ssafy.checkmate.domain.member.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class InvalidItemImageException extends BusinessException {
    public InvalidItemImageException() {
        super(MemberItemErrorCode.MEMBER_ITEM_INVALID_IMAGE);
    }
}
