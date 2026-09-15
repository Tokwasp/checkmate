package ssafy.checkmate.domain.trip.dto.response;

import java.util.List;
import lombok.Getter;

@Getter
public class TripListResponse {

    private final List<TripSummaryResponse> trips;

    private TripListResponse(List<TripSummaryResponse> trips) {
        this.trips = trips;
    }

    public static TripListResponse of(List<TripSummaryResponse> trips) {
        return new TripListResponse(trips);
    }
}
