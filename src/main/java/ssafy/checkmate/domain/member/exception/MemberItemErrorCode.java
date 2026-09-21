package ssafy.checkmate.domain.member.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import ssafy.checkmate.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum MemberItemErrorCode implements ErrorCode {
    MEMBER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 물건입니다."),
    MEMBER_ITEM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 물건만 수정할 수 있습니다."),
    MEMBER_ITEM_INVALID_IMAGE(HttpStatus.BAD_REQUEST, "물건 사진이 유효하지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
