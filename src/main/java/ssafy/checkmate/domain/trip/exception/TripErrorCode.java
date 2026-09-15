package ssafy.checkmate.domain.trip.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import ssafy.checkmate.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum TripErrorCode implements ErrorCode {

    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 여행입니다."),
    TRIP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 여행만 접근할 수 있습니다."),
    TRIP_REQUIRED_INFO_MISSING(HttpStatus.BAD_REQUEST, "필수 여행 정보가 누락되었습니다."),
    TRIP_INVALID_DESTINATION(HttpStatus.BAD_REQUEST, "유효하지 않은 목적지입니다.");

    private final HttpStatus status;
    private final String message;
}
