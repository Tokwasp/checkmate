package ssafy.checkmate.domain.member.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class MemberItemNotFoundException extends BusinessException {
    public MemberItemNotFoundException() {
        super(MemberItemErrorCode.MEMBER_ITEM_NOT_FOUND);
    }
}
