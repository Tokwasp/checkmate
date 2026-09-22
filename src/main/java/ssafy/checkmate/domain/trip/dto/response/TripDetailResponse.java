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
    /**
     * 목적지 좌표·목적·항공편 유무 (2026-09-22 추가). 여행 수정(PUT)이 생성과 같은 전체 본문을 요구하는데,
     * 앱이 이 값들을 알 길이 없어 목적지를 다시 고르지 않으면 "목적지를 목록에서 다시 골라 주세요" 가 떴다.
     * 상세에 실어 주면 앱이 바꾸지 않은 값을 그대로 되돌려 보낼 수 있다.
     */
    private final Double latitude;
    private final Double longitude;
    private final List<String> purposes;
    private final boolean hasFlight;
    private final String departureAirline;
    /** 귀국(입국) 항공사. 항공편 없는 여행은 null. */
    private final String arrivalAirline;
    /** 여행 표지 사진. 안 골랐으면 null */
    private final String coverImageUrl;
    private final int currentStep;
    private final int totalStep;
    private final String currentStepName;
    private final int totalItemCount;
    private final int recognizedItemCount;
    /**
     * 두고 온 물건 수 — 출국 때 담았는데(departureChecked) 귀가 전 점검에서 확인되지 않은(returnChecked=false) 물품.
     * 점검을 아직 안 했으면 담은 물품 수와 같다. 마이페이지 여행 기록이 쓴다.
     */
    private final int leftBehindCount;
    private final List<TripBagResponse> bags;

    private TripDetailResponse(TripType tripType, String country, String region, LocalDate startDate, LocalDate endDate,
                                Double latitude, Double longitude, List<String> purposes, boolean hasFlight,
                                String departureAirline, String arrivalAirline, String coverImageUrl,
                                int currentStep, int totalStep, String currentStepName,
                                int totalItemCount, int recognizedItemCount, int leftBehindCount,
                                List<TripBagResponse> bags) {
        this.tripType = tripType;
        this.country = country;
        this.region = region;
        this.startDate = startDate;
        this.endDate = endDate;
        this.latitude = latitude;
        this.longitude = longitude;
        this.purposes = purposes;
        this.hasFlight = hasFlight;
        this.departureAirline = departureAirline;
        this.arrivalAirline = arrivalAirline;
        this.coverImageUrl = coverImageUrl;
        this.currentStep = currentStep;
        this.totalStep = totalStep;
        this.currentStepName = currentStepName;
        this.totalItemCount = totalItemCount;
        this.recognizedItemCount = recognizedItemCount;
        this.leftBehindCount = leftBehindCount;
        this.bags = bags;
    }

    public static TripDetailResponse of(Trip trip, LocalDate today, int totalItemCount, int recognizedItemCount,
                                         int leftBehindCount, List<TripBagResponse> bags) {
        TripStep step = trip.getCurrentStep();
        return new TripDetailResponse(trip.getType(today), trip.getCountry(), trip.getRegion(), trip.getStartDate(),
                trip.getEndDate(), trip.getLatitude(), trip.getLongitude(), trip.getPurposes(), trip.isHasFlight(),
                trip.getDepartureAirline(), trip.getArrivalAirline(), trip.getCoverImageUrl(),
                step.getOrder(), TripStep.totalStepCount(), step.getDisplayName(),
                totalItemCount, recognizedItemCount, leftBehindCount, bags);
    }
}
