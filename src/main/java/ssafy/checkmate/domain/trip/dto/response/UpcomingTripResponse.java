package ssafy.checkmate.domain.trip.dto.response;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import ssafy.checkmate.domain.trip.entity.Trip;

@Getter
public class UpcomingTripResponse {

    private final Long tripId;
    private final String destination;
    private final int dDay;
    private final int packedItemCount;
    private final int totalItemCount;

    private UpcomingTripResponse(Long tripId, String destination, int dDay,
                                  int packedItemCount, int totalItemCount) {
        this.tripId = tripId;
        this.destination = destination;
        this.dDay = dDay;
        this.packedItemCount = packedItemCount;
        this.totalItemCount = totalItemCount;
    }

    public static UpcomingTripResponse of(Trip trip, LocalDate today, int packedItemCount, int totalItemCount) {
        int dDay = (int) ChronoUnit.DAYS.between(today, trip.getStartDate());
        return new UpcomingTripResponse(trip.getId(), trip.getDestination(), dDay, packedItemCount, totalItemCount);
    }
}
