package ssafy.checkmate.domain.trip.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class InvalidCoverImageException extends BusinessException {
    public InvalidCoverImageException() {
        super(TripErrorCode.TRIP_INVALID_COVER_IMAGE);
    }
}
