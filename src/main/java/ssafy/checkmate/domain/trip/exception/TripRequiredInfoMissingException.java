package ssafy.checkmate.domain.trip.exception;

import ssafy.checkmate.global.exception.BusinessException;

public class TripRequiredInfoMissingException extends BusinessException {

    public TripRequiredInfoMissingException() {
        super(TripErrorCode.TRIP_REQUIRED_INFO_MISSING);
    }
}
