package ssafy.checkmate.domain.checklist.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import ssafy.checkmate.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum BagErrorCode implements ErrorCode {

    BAG_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 가방입니다."),
    BAG_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 가방만 접근할 수 있습니다."),
    BAG_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "가방 이름이 누락되었습니다."),
    INVALID_CARRIAGE_TYPE(HttpStatus.BAD_REQUEST, "가방 구분이 올바르지 않습니다."),
    DEFAULT_BAG_NOT_MODIFIABLE(HttpStatus.BAD_REQUEST, "기본 가방은 수정할 수 없습니다."),
    DEFAULT_BAG_NOT_DELETABLE(HttpStatus.BAD_REQUEST, "기본 가방은 삭제할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
