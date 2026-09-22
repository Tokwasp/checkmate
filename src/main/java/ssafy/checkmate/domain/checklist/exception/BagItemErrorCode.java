package ssafy.checkmate.domain.checklist.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import ssafy.checkmate.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum BagItemErrorCode implements ErrorCode {

    BAG_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 체크리스트 항목입니다."),
    BAG_ITEM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 체크리스트만 접근할 수 있습니다."),
    MIXED_BAG_ITEMS(HttpStatus.BAD_REQUEST, "선택한 항목들이 같은 가방에 속해 있지 않습니다."),
    BAG_ITEM_UPDATE_EMPTY(HttpStatus.BAD_REQUEST, "수정할 내용이 없습니다."),
    BAG_ITEM_BAG_MISMATCH(HttpStatus.BAD_REQUEST, "해당 가방에 존재하지 않는 물품입니다."),
    BAG_ITEM_DELETE_EMPTY(HttpStatus.BAD_REQUEST, "삭제할 항목이 없습니다."),
    NOT_RETURN_CHECK_TARGET(HttpStatus.BAD_REQUEST, "점검 대상 물품이 아닙니다.");

    private final HttpStatus status;
    private final String message;
}
