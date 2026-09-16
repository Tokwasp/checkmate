package ssafy.checkmate.domain.trip.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.service.BagService;
import ssafy.checkmate.domain.trip.dto.request.TripCreateRequest;
import ssafy.checkmate.domain.trip.dto.response.PackingProgressResponse;
import ssafy.checkmate.domain.trip.dto.response.PlaceDetailResponse;
import ssafy.checkmate.domain.trip.dto.response.PlaceSuggestionResponse;
import ssafy.checkmate.domain.trip.dto.response.TripBagItemResponse;
import ssafy.checkmate.domain.trip.dto.response.TripBagResponse;
import ssafy.checkmate.domain.trip.dto.response.TripCreateResponse;
import ssafy.checkmate.domain.trip.dto.response.TripDetailResponse;
import ssafy.checkmate.domain.trip.dto.response.TripListResponse;
import ssafy.checkmate.domain.trip.dto.response.TripSummaryResponse;
import ssafy.checkmate.domain.trip.dto.response.TripWeatherResponse;
import ssafy.checkmate.domain.trip.dto.response.UpcomingTripResponse;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.exception.TripRequiredInfoMissingException;
import ssafy.checkmate.domain.trip.place.GooglePlacesClient;
import ssafy.checkmate.domain.trip.place.PlaceDetails;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.domain.weather.DailyWeather;
import ssafy.checkmate.domain.weather.WeatherClient;
import ssafy.checkmate.domain.weather.entity.TripWeather;
import ssafy.checkmate.domain.weather.repository.TripWeatherRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final BagRepository bagRepository;
    private final BagItemRepository bagItemRepository;
    private final BagService bagService;
    private final GooglePlacesClient googlePlacesClient;
    private final WeatherClient weatherClient;
    private final TripWeatherRepository tripWeatherRepository;

    public List<PlaceSuggestionResponse> autocompleteDestination(String query, String sessionToken) {
        return googlePlacesClient.autocomplete(query, sessionToken).stream()
                .map(PlaceSuggestionResponse::of)
                .toList();
    }

    public PlaceDetailResponse getDestinationDetails(String placeId, String sessionToken) {
        PlaceDetails placeDetails = googlePlacesClient.getDetails(placeId, sessionToken);
        return PlaceDetailResponse.of(placeDetails);
    }

    @Transactional
    public TripCreateResponse createTrip(Long memberId, TripCreateRequest request) {
        validateFlightInfo(request.getHasFlight(), request.getDepartureAirline(), request.getArrivalAirline());
        PlaceDetails placeDetails = googlePlacesClient.getDetails(request.getPlaceId(), request.getSessionToken());

        Trip trip = tripRepository.save(Trip.builder()
                .memberId(memberId)
                .country(request.getCountry())
                .region(request.getRegion())
                .latitude(placeDetails.getLatitude())
                .longitude(placeDetails.getLongitude())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .purposes(request.getPurposes())
                .hasFlight(request.getHasFlight())
                .departureAirline(request.getDepartureAirline())
                .arrivalAirline(request.getArrivalAirline())
                .build());
        saveWeatherForecast(trip);

        Bag defaultBag = bagService.ensureDefaultBag(trip.getId(), memberId);
        return TripCreateResponse.of(trip, defaultBag.getId());
    }

    private void saveWeatherForecast(Trip trip) {
        List<DailyWeather> forecast = weatherClient.getForecast(
                trip.getLatitude(), trip.getLongitude(), trip.getStartDate(), trip.getEndDate());
        List<TripWeather> weathers = forecast.stream()
                .map(daily -> TripWeather.of(trip.getId(), daily))
                .toList();
        tripWeatherRepository.saveAll(weathers);
    }

    public TripListResponse getTrips(Long memberId, LocalDate today) {
        List<Trip> trips = tripRepository.findUpcomingTrips(memberId, today);
        List<TripSummaryResponse> summaries = trips.stream()
                .map(trip -> {
                    int totalItemCount = (int) bagItemRepository.countByTripId(trip.getId());
                    int packedItemCount = (int) bagItemRepository.countByTripIdAndDepartureChecked(trip.getId(), true);
                    List<TripWeatherResponse> weathers = tripWeatherRepository.findAllByTripIdOrderByDateAsc(trip.getId()).stream()
                            .map(TripWeather::toDailyWeather)
                            .map(TripWeatherResponse::of)
                            .toList();
                    return TripSummaryResponse.of(trip, packedItemCount, totalItemCount, weathers, today);
                })
                .toList();
        return TripListResponse.of(summaries);
    }

    /** 메인 화면 짐싸기 진척도 위젯용 — 아직 시작하지 않은 여행 중 출국일이 가장 임박한 1건. */
    public UpcomingTripResponse getUpcomingTrip(Long memberId, LocalDate today) {
        return tripRepository.findFirstByMemberIdAndStartDateGreaterThanEqualOrderByEndDateAsc(memberId, today)
                .map(trip -> {
                    int totalItemCount = (int) bagItemRepository.countByTripId(trip.getId());
                    int packedItemCount = (int) bagItemRepository.countByTripIdAndDepartureChecked(trip.getId(), true);
                    return UpcomingTripResponse.of(trip, today, packedItemCount, totalItemCount);
                })
                .orElse(null);
    }

    public TripDetailResponse getTripDetail(Long tripId, Long memberId, LocalDate today) {
        Trip trip = findTripOwnedBy(tripId, memberId, "본인의 여행만 조회할 수 있습니다.");

        List<BagItem> items = bagItemRepository.findAllByTripId(tripId);
        Map<Long, List<BagItem>> itemsByBagId = items.stream().collect(Collectors.groupingBy(BagItem::getBagId));

        List<TripBagResponse> bags = bagRepository.findAllByTripId(tripId).stream()
                .map(bag -> {
                    List<TripBagItemResponse> bagItems = itemsByBagId.getOrDefault(bag.getId(), List.of()).stream()
                            .map(TripBagItemResponse::of)
                            .toList();
                    return TripBagResponse.of(bag, bagItems);
                })
                .toList();

        int totalItemCount = items.size();
        int recognizedItemCount = (int) items.stream().filter(BagItem::isDepartureChecked).count();

        return TripDetailResponse.of(trip, today, totalItemCount, recognizedItemCount, bags);
    }

    @Transactional
    public void updateTrip(Long tripId, Long memberId, TripCreateRequest request) {
        Trip trip = findTripOwnedBy(tripId, memberId, "본인의 여행만 수정할 수 있습니다.");
        validateFlightInfo(request.getHasFlight(), request.getDepartureAirline(), request.getArrivalAirline());
        PlaceDetails placeDetails = googlePlacesClient.getDetails(request.getPlaceId(), request.getSessionToken());

        trip.update(request.getCountry(), request.getRegion(), placeDetails.getLatitude(), placeDetails.getLongitude(),
                request.getStartDate(), request.getEndDate(), request.getPurposes(),
                request.getHasFlight(), request.getDepartureAirline(), request.getArrivalAirline());
    }

    @Transactional
    public void deleteTrip(Long tripId, Long memberId) {
        Trip trip = findTripOwnedBy(tripId, memberId, "본인의 여행만 삭제할 수 있습니다.");
        bagItemRepository.deleteAllByTripId(trip.getId());
        bagRepository.deleteAllByTripId(trip.getId());
        tripWeatherRepository.deleteAllByTripId(trip.getId());
        tripRepository.delete(trip);
    }

    public PackingProgressResponse getPackingSession(Long tripId, Long memberId) {
        Trip trip = findTripOwnedBy(tripId, memberId, "본인의 여행만 조회할 수 있습니다.");
        List<BagItem> items = bagItemRepository.findAllByTripId(tripId);

        int totalItemCount = items.size();
        int packedItemCount = (int) items.stream().filter(BagItem::isDepartureChecked).count();
        LocalDateTime lastUpdatedAt = items.stream()
                .map(BagItem::getUpdatedAt)
                .max(Comparator.naturalOrder())
                .orElseGet(trip::getCreatedAt);

        return PackingProgressResponse.of(packedItemCount, totalItemCount, lastUpdatedAt);
    }

    private void validateFlightInfo(Boolean hasFlight, String departureAirline, String arrivalAirline) {
        if (Boolean.TRUE.equals(hasFlight) && (isBlank(departureAirline) || isBlank(arrivalAirline))) {
            throw new TripRequiredInfoMissingException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Trip findTripOwnedBy(Long tripId, Long memberId, String accessDeniedMessage) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        if (!trip.isOwnedBy(memberId)) {
            throw new TripAccessDeniedException(accessDeniedMessage);
        }
        return trip;
    }
}
