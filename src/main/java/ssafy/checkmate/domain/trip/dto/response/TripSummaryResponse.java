package ssafy.checkmate.domain.trip.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.entity.TripStep;
import ssafy.checkmate.domain.trip.entity.TripType;

@Getter
public class TripSummaryResponse {

    private final Long tripId;
    private final String destination;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int packedItemCount;
    private final int totalItemCount;
    private final TripType tripType;
    private final int currentStep;
    private final int totalStep;
    private final String currentStepName;
    private final List<TripWeatherResponse> weathers;

    private TripSummaryResponse(Long tripId, String destination, LocalDate startDate, LocalDate endDate,
                                 int packedItemCount, int totalItemCount, TripType tripType,
                                 int currentStep, int totalStep, String currentStepName,
                                 List<TripWeatherResponse> weathers) {
        this.tripId = tripId;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.packedItemCount = packedItemCount;
        this.totalItemCount = totalItemCount;
        this.tripType = tripType;
        this.currentStep = currentStep;
        this.totalStep = totalStep;
        this.currentStepName = currentStepName;
        this.weathers = weathers;
    }

    public static TripSummaryResponse of(Trip trip, int packedItemCount, int totalItemCount,
                                          List<TripWeatherResponse> weathers, LocalDate today) {
        TripStep step = trip.getCurrentStep();
        return new TripSummaryResponse(trip.getId(), trip.getDestination(), trip.getStartDate(),
                trip.getEndDate(), packedItemCount, totalItemCount, trip.getType(today),
                step.getOrder(), TripStep.totalStepCount(), step.getDisplayName(), weathers);
    }
}
