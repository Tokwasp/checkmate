package ssafy.checkmate.domain.trip.dto.response;

import java.time.LocalDate;
import lombok.Getter;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.entity.TripType;

@Getter
public class TripSummaryResponse {

    private final Long tripId;
    private final String destination;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int checklistCount;
    private final TripType tripType;

    private TripSummaryResponse(Long tripId, String destination, LocalDate startDate, LocalDate endDate,
                                 int checklistCount, TripType tripType) {
        this.tripId = tripId;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.checklistCount = checklistCount;
        this.tripType = tripType;
    }

    public static TripSummaryResponse of(Trip trip, int checklistCount, LocalDate today) {
        return new TripSummaryResponse(trip.getId(), trip.getDestination(), trip.getStartDate(),
                trip.getEndDate(), checklistCount, trip.getType(today));
    }
}
