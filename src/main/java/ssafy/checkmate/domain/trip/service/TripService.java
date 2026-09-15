package ssafy.checkmate.domain.trip.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
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
import ssafy.checkmate.domain.trip.dto.response.PlaceSuggestionResponse;
import ssafy.checkmate.domain.trip.dto.response.TripCreateResponse;
import ssafy.checkmate.domain.trip.dto.response.TripListResponse;
import ssafy.checkmate.domain.trip.dto.response.TripSummaryResponse;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.exception.TripRequiredInfoMissingException;
import ssafy.checkmate.domain.trip.place.GooglePlacesClient;
import ssafy.checkmate.domain.trip.place.PlaceDetails;
import ssafy.checkmate.domain.trip.repository.TripRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final BagRepository bagRepository;
    private final BagItemRepository bagItemRepository;
    private final BagService bagService;
    private final GooglePlacesClient googlePlacesClient;

    public List<PlaceSuggestionResponse> autocompleteDestination(String query, String sessionToken) {
        return googlePlacesClient.autocomplete(query, sessionToken).stream()
                .map(PlaceSuggestionResponse::of)
                .toList();
    }

    @Transactional
    public TripCreateResponse createTrip(Long memberId, TripCreateRequest request) {
        validateFlightInfo(request.getHasFlight(), request.getDepartureAirline(), request.getArrivalAirline());
        PlaceDetails placeDetails = googlePlacesClient.getDetails(request.getPlaceId(), request.getSessionToken());

        Trip trip = tripRepository.save(Trip.builder()
                .memberId(memberId)
                .destination(placeDetails.getName())
                .latitude(placeDetails.getLatitude())
                .longitude(placeDetails.getLongitude())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .purposes(request.getPurposes())
                .hasFlight(request.getHasFlight())
                .departureAirline(request.getDepartureAirline())
                .arrivalAirline(request.getArrivalAirline())
                .build());

        Bag defaultBag = bagService.ensureDefaultBag(trip.getId(), memberId);
        return TripCreateResponse.of(trip, defaultBag.getId());
    }

    public TripListResponse getTrips(Long memberId, LocalDate today) {
        List<Trip> trips = tripRepository.findAllByMemberId(memberId);
        List<TripSummaryResponse> summaries = trips.stream()
                .map(trip -> TripSummaryResponse.of(trip, (int) bagItemRepository.countByTripId(trip.getId()), today))
                .toList();
        return TripListResponse.of(summaries);
    }

    @Transactional
    public void updateTrip(Long tripId, Long memberId, TripCreateRequest request) {
        Trip trip = findTripOwnedBy(tripId, memberId, "본인의 여행만 수정할 수 있습니다.");
        validateFlightInfo(request.getHasFlight(), request.getDepartureAirline(), request.getArrivalAirline());
        PlaceDetails placeDetails = googlePlacesClient.getDetails(request.getPlaceId(), request.getSessionToken());

        trip.update(placeDetails.getName(), placeDetails.getLatitude(), placeDetails.getLongitude(),
                request.getStartDate(), request.getEndDate(), request.getPurposes(),
                request.getHasFlight(), request.getDepartureAirline(), request.getArrivalAirline());
    }

    @Transactional
    public void deleteTrip(Long tripId, Long memberId) {
        Trip trip = findTripOwnedBy(tripId, memberId, "본인의 여행만 삭제할 수 있습니다.");
        bagItemRepository.deleteAllByTripId(trip.getId());
        bagRepository.deleteAllByTripId(trip.getId());
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
