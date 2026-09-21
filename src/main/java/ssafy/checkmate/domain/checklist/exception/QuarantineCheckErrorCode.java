package ssafy.checkmate.domain.checklist.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import ssafy.checkmate.global.exception.ErrorCode;

/** 촬영 물품 반입 판정(35번) 에러. 이미지가 아예 없거나 비었을 때는 26번과 같은 {@link ScanErrorCode#INVALID_SCAN_IMAGE}를 쓴다. */
@Getter
@RequiredArgsConstructor
public enum QuarantineCheckErrorCode implements ErrorCode {

    UNSUPPORTED_SCAN_IMAGE(HttpStatus.BAD_REQUEST, "이미지는 5MB 이하의 JPEG, PNG, WebP만 올릴 수 있습니다."),
    ITEM_NOT_RECOGNIZED(HttpStatus.UNPROCESSABLE_CONTENT, "물품을 인식하지 못했습니다."),
    ITEM_ANALYSIS_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "물품 분석 횟수를 모두 사용했습니다."),
    ITEM_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "물품 이미지 분석에 실패했습니다."),
    QUARANTINE_CHECK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "규정 검사에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
