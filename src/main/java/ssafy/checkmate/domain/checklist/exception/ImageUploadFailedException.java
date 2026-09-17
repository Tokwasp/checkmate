package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class ImageUploadFailedException extends BusinessException {

    public ImageUploadFailedException() {
        super(ScanErrorCode.IMAGE_UPLOAD_FAILED);
    }
}
