package ssafy.checkmate.domain.trip.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.entity.TripStep;
import ssafy.checkmate.domain.trip.entity.TripType;

@Getter
public class TripDetailResponse {

    private final TripType tripType;
    private final String country;
    private final String region;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String departureAirline;
    private final int currentStep;
    private final int totalStep;
    private final String currentStepName;
    private final int totalItemCount;
    private final int recognizedItemCount;
    private final List<TripBagResponse> bags;

    private TripDetailResponse(TripType tripType, String country, String region, LocalDate startDate, LocalDate endDate,
                                String departureAirline, int currentStep, int totalStep, String currentStepName,
                                int totalItemCount, int recognizedItemCount, List<TripBagResponse> bags) {
        this.tripType = tripType;
        this.country = country;
        this.region = region;
        this.startDate = startDate;
        this.endDate = endDate;
        this.departureAirline = departureAirline;
        this.currentStep = currentStep;
        this.totalStep = totalStep;
        this.currentStepName = currentStepName;
        this.totalItemCount = totalItemCount;
        this.recognizedItemCount = recognizedItemCount;
        this.bags = bags;
    }

    public static TripDetailResponse of(Trip trip, LocalDate today, int totalItemCount, int recognizedItemCount,
                                         List<TripBagResponse> bags) {
        TripStep step = trip.getCurrentStep();
        return new TripDetailResponse(trip.getType(today), trip.getCountry(), trip.getRegion(), trip.getStartDate(),
                trip.getEndDate(), trip.getDepartureAirline(),
                step.getOrder(), TripStep.totalStepCount(), step.getDisplayName(),
                totalItemCount, recognizedItemCount, bags);
    }
}
