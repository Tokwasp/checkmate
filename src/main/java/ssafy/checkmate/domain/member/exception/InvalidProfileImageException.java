package ssafy.checkmate.domain.member.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class InvalidProfileImageException extends BusinessException {
    public InvalidProfileImageException() {
        super(MemberErrorCode.INVALID_PROFILE_IMAGE);
    }
}
