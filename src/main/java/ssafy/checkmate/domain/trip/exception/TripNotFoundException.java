package ssafy.checkmate.domain.trip.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class TripNotFoundException extends BusinessException {

    public TripNotFoundException() {
        super(TripErrorCode.TRIP_NOT_FOUND);
    }
}
