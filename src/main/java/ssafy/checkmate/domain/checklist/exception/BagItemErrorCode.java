package ssafy.checkmate.domain.checklist.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import ssafy.checkmate.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum BagItemErrorCode implements ErrorCode {

    BAG_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 체크리스트 항목입니다."),
    BAG_ITEM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 체크리스트만 접근할 수 있습니다.");

    private final HttpStatus status;
    private final String message;
}
