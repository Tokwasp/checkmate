package ssafy.checkmate.domain.member.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import ssafy.checkmate.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    INVALID_PROFILE_IMAGE(HttpStatus.BAD_REQUEST, "프로필 사진이 유효하지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
