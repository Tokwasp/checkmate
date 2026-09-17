package ssafy.checkmate.domain.checklist.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import ssafy.checkmate.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum ScanErrorCode implements ErrorCode {

    INVALID_SCAN_IMAGE(HttpStatus.BAD_REQUEST, "물체 이미지가 유효하지 않습니다."),
    INVALID_REGULATION_TYPE(HttpStatus.BAD_REQUEST, "유효하지 않은 규정 타입입니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
