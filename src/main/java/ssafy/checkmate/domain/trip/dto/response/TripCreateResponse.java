package ssafy.checkmate.domain.trip.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.trip.entity.Trip;

@Getter
public class TripCreateResponse {

    private final Long tripId;
    private final Long defaultBagId;

    private TripCreateResponse(Long tripId, Long defaultBagId) {
        this.tripId = tripId;
        this.defaultBagId = defaultBagId;
    }

    public static TripCreateResponse of(Trip trip, Long defaultBagId) {
        return new TripCreateResponse(trip.getId(), defaultBagId);
    }
}
