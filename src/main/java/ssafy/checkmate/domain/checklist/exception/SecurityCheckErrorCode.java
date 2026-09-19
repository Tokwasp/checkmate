package ssafy.checkmate.domain.checklist.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import ssafy.checkmate.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum SecurityCheckErrorCode implements ErrorCode {

    NO_ITEMS_TO_CHECK(HttpStatus.BAD_REQUEST, "검사할 물품이 없습니다."),
    UNSUPPORTED_COUNTRY(HttpStatus.UNPROCESSABLE_CONTENT, "아직 지원하지 않는 국가입니다."),
    SECURITY_CHECK_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "반입 규정 검사 횟수를 모두 사용했습니다."),
    SECURITY_CHECK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "규정 검사에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
