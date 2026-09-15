package ssafy.checkmate.domain.trip.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class TripInvalidDestinationException extends BusinessException {

    public TripInvalidDestinationException() {
        super(TripErrorCode.TRIP_INVALID_DESTINATION);
    }
}
