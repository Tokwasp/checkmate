package ssafy.checkmate.domain.member.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class MemberItemAccessDeniedException extends BusinessException {
    public MemberItemAccessDeniedException() {
        super(MemberItemErrorCode.MEMBER_ITEM_ACCESS_DENIED);
    }
}
