package ssafy.checkmate.domain.trip.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class TripAccessDeniedException extends BusinessException {

    public TripAccessDeniedException(String message) {
        super(TripErrorCode.TRIP_ACCESS_DENIED, message);
    }
}
