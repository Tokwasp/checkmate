package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class InvalidScanImageException extends BusinessException {

    public InvalidScanImageException() {
        super(ScanErrorCode.INVALID_SCAN_IMAGE);
    }
}
