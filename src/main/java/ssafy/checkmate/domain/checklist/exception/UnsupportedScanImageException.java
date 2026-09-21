package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class UnsupportedScanImageException extends BusinessException {

    public UnsupportedScanImageException() {
        super(QuarantineCheckErrorCode.UNSUPPORTED_SCAN_IMAGE);
    }
}
