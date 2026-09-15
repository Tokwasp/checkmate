package ssafy.checkmate.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static ssafy.checkmate.domain.trip.TestRequestFactory.tripCreateRequest;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.trip.dto.request.TripCreateRequest;
import ssafy.checkmate.domain.trip.dto.response.PackingProgressResponse;
import ssafy.checkmate.domain.trip.dto.response.PlaceSuggestionResponse;
import ssafy.checkmate.domain.trip.dto.response.TripCreateResponse;
import ssafy.checkmate.domain.trip.dto.response.TripListResponse;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.entity.TripType;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripInvalidDestinationException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.exception.TripRequiredInfoMissingException;
import ssafy.checkmate.domain.trip.place.GooglePlacesClient;
import ssafy.checkmate.domain.trip.place.PlaceDetails;
import ssafy.checkmate.domain.trip.place.PlaceSuggestion;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.domain.trip.service.TripService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TripServiceTest {

    @Autowired
    private TripService tripService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @MockitoBean
    private GooglePlacesClient googlePlacesClient;

    @Test
    @DisplayName("목적지를 검색하면 관련도 높은 순 후보 목록을 반환한다")
    void autocompleteDestination() {
        // given
        given(googlePlacesClient.autocomplete("도쿄", "session-1")).willReturn(List.of(
                new PlaceSuggestion("place-1", "도쿄, 일본"),
                new PlaceSuggestion("place-2", "도쿄도, 일본")));

        // when
        List<PlaceSuggestionResponse> response = tripService.autocompleteDestination("도쿄", "session-1");

        // then
        assertThat(response).hasSize(2)
                .extracting(PlaceSuggestionResponse::getPlaceId, PlaceSuggestionResponse::getDescription)
                .containsExactly(
                        tuple("place-1", "도쿄, 일본"),
                        tuple("place-2", "도쿄도, 일본"));
    }

    @Test
    @DisplayName("여행을 생성하면 Google Places로 확인한 목적지/좌표가 저장되고 기본 가방이 함께 생성된다")
    void createTrip() {
        // given
        given(googlePlacesClient.getDetails("place-1", "session-1"))
                .willReturn(new PlaceDetails("도쿄", 35.6895, 139.69171));
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(3);
        TripCreateRequest request = tripCreateRequest("place-1", "session-1", startDate, endDate, List.of("관광", "미식"));

        // when
        TripCreateResponse response = tripService.createTrip(1L, request);

        // then
        assertThat(response.getTripId()).isNotNull();
        assertThat(response.getDefaultBagId()).isNotNull();

        Trip saved = tripRepository.findById(response.getTripId()).orElseThrow();
        assertThat(saved.getDestination()).isEqualTo("도쿄");
        assertThat(saved.getLatitude()).isEqualTo(35.6895);
        assertThat(saved.getLongitude()).isEqualTo(139.69171);
        assertThat(saved.getPurposes()).containsExactly("관광", "미식");
        assertThat(saved.isHasFlight()).isFalse();

        Bag defaultBag = bagRepository.findById(response.getDefaultBagId()).orElseThrow();
        assertThat(defaultBag.isDefault()).isTrue();
    }

    @Test
    @DisplayName("항공편을 이용하면 출국/입국 항공사를 함께 저장한다")
    void createTripWithFlight() {
        // given
        given(googlePlacesClient.getDetails("place-1", "session-1"))
                .willReturn(new PlaceDetails("도쿄", 35.6895, 139.69171));
        TripCreateRequest request = tripCreateRequest("place-1", "session-1",
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), List.of("관광"),
                true, "대한항공", "피치항공");

        // when
        TripCreateResponse response = tripService.createTrip(1L, request);

        // then
        Trip saved = tripRepository.findById(response.getTripId()).orElseThrow();
        assertThat(saved.isHasFlight()).isTrue();
        assertThat(saved.getDepartureAirline()).isEqualTo("대한항공");
        assertThat(saved.getArrivalAirline()).isEqualTo("피치항공");
    }

    @Test
    @DisplayName("항공편을 이용하는데 항공사 정보가 없으면 예외가 발생한다")
    void createTripWithFlightWithoutAirline() {
        // given
        TripCreateRequest request = tripCreateRequest("place-1", "session-1",
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), List.of("관광"),
                true, null, null);

        // when // then
        assertThatThrownBy(() -> tripService.createTrip(1L, request))
                .isInstanceOf(TripRequiredInfoMissingException.class)
                .hasMessage("필수 여행 정보가 누락되었습니다.");
    }

    @Test
    @DisplayName("유효하지 않은 목적지로 여행을 생성하면 예외가 발생한다")
    void createTripWithInvalidPlace() {
        // given
        given(googlePlacesClient.getDetails("invalid-place", "session-1"))
                .willThrow(new TripInvalidDestinationException());
        TripCreateRequest request = tripCreateRequest("invalid-place", "session-1",
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), List.of("관광"));

        // when // then
        assertThatThrownBy(() -> tripService.createTrip(1L, request))
                .isInstanceOf(TripInvalidDestinationException.class)
                .hasMessage("유효하지 않은 목적지입니다.");
    }

    @Test
    @DisplayName("여행 목적을 선택하지 않아도 여행을 생성할 수 있다")
    void createTripWithNullPurposes() {
        // given
        given(googlePlacesClient.getDetails("place-1", "session-1"))
                .willReturn(new PlaceDetails("도쿄", 35.6895, 139.69171));
        TripCreateRequest request = tripCreateRequest("place-1", "session-1",
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), null);

        // when
        TripCreateResponse response = tripService.createTrip(1L, request);

        // then
        Trip saved = tripRepository.findById(response.getTripId()).orElseThrow();
        assertThat(saved.getPurposes()).isEmpty();
    }

    @Test
    @DisplayName("여행 목록을 진행 중/지난 여행으로 나누고 체크리스트 개수를 함께 반환한다")
    void getTrips() {
        // given
        LocalDate today = LocalDate.now();
        Trip currentTrip = createTrip(1L, today.plusDays(1), today.plusDays(3));
        Trip pastTrip = createTrip(1L, today.minusDays(10), today.minusDays(5));
        Bag defaultBag = bagRepository.save(Bag.createDefault(currentTrip.getId(), 1L));
        bagItemRepository.save(BagItem.create(currentTrip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));
        bagItemRepository.save(BagItem.create(currentTrip.getId(), 1L, "우산", "생활용품", 1, defaultBag.getId()));

        // when
        TripListResponse response = tripService.getTrips(1L, today);

        // then
        assertThat(response.getTrips()).hasSize(2)
                .extracting("tripId", "checklistCount", "tripType")
                .containsExactlyInAnyOrder(
                        tuple(currentTrip.getId(), 2, TripType.CURRENT),
                        tuple(pastTrip.getId(), 0, TripType.PAST));
    }

    @Test
    @DisplayName("여행이 하나도 없으면 빈 목록을 반환한다")
    void getTripsWhenEmpty() {
        // when
        TripListResponse response = tripService.getTrips(1L, LocalDate.now());

        // then
        assertThat(response.getTrips()).isEmpty();
    }

    @Test
    @DisplayName("종료일이 오늘이면 진행 중인 여행으로 분류한다")
    void getTripsWhenEndDateIsToday() {
        // given
        LocalDate today = LocalDate.now();
        Trip trip = createTrip(1L, today.minusDays(2), today);

        // when
        TripListResponse response = tripService.getTrips(1L, today);

        // then
        assertThat(response.getTrips()).hasSize(1)
                .extracting("tripType")
                .containsExactly(TripType.CURRENT);
    }

    @Test
    @DisplayName("여행 정보를 수정하면 Google Places로 다시 확인한 목적지/좌표로 갱신된다")
    void updateTrip() {
        // given
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        given(googlePlacesClient.getDetails("place-2", "session-2"))
                .willReturn(new PlaceDetails("오사카", 34.6937, 135.5023));
        TripCreateRequest request = tripCreateRequest("place-2", "session-2",
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(8), List.of("쇼핑"));

        // when
        tripService.updateTrip(trip.getId(), 1L, request);

        // then
        Trip updated = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(updated.getDestination()).isEqualTo("오사카");
        assertThat(updated.getLatitude()).isEqualTo(34.6937);
        assertThat(updated.getLongitude()).isEqualTo(135.5023);
        assertThat(updated.getPurposes()).containsExactly("쇼핑");
    }

    @Test
    @DisplayName("존재하지 않는 여행을 수정하면 예외가 발생한다")
    void updateTripNotFound() {
        TripCreateRequest request = tripCreateRequest("place-1", "session-1",
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), List.of("관광"));

        assertThatThrownBy(() -> tripService.updateTrip(999L, 1L, request))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    @DisplayName("본인의 여행이 아니면 수정할 수 없다")
    void updateTripWithNotOwner() {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        TripCreateRequest request = tripCreateRequest("place-1", "session-1",
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), List.of("관광"));

        assertThatThrownBy(() -> tripService.updateTrip(trip.getId(), 2L, request))
                .isInstanceOf(TripAccessDeniedException.class);
    }

    @Test
    @DisplayName("여행을 삭제하면 속한 가방과 체크리스트 항목도 함께 삭제된다")
    void deleteTrip() {
        // given
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        Bag bag = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, bag.getId()));

        // when
        tripService.deleteTrip(trip.getId(), 1L);

        // then
        assertThat(tripRepository.findById(trip.getId())).isEmpty();
        assertThat(bagRepository.findById(bag.getId())).isEmpty();
        assertThat(bagItemRepository.findById(item.getId())).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 여행을 삭제하면 예외가 발생한다")
    void deleteTripNotFound() {
        assertThatThrownBy(() -> tripService.deleteTrip(999L, 1L))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    @DisplayName("본인의 여행이 아니면 삭제할 수 없다")
    void deleteTripWithNotOwner() {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        assertThatThrownBy(() -> tripService.deleteTrip(trip.getId(), 2L))
                .isInstanceOf(TripAccessDeniedException.class);
    }

    @Test
    @DisplayName("짐싸기 진척도를 담은 물품 수/전체 물품 수로 계산한다")
    void getPackingSession() {
        // given
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        BagItem checked = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));
        checked.update(null, null, null, true, null);
        bagItemRepository.save(BagItem.create(trip.getId(), 1L, "우산", "생활용품", 1, defaultBag.getId()));
        bagItemRepository.save(BagItem.create(trip.getId(), 1L, "충전기", "전자기기", 1, defaultBag.getId()));

        // when
        PackingProgressResponse response = tripService.getPackingSession(trip.getId(), 1L);

        // then
        assertThat(response.getPackedItemCount()).isEqualTo(1);
        assertThat(response.getTotalItemCount()).isEqualTo(3);
        assertThat(response.getLastUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("체크리스트 항목이 하나도 없으면 짐싸기 진척도는 0/0이고 여행 생성 시각을 마지막 작업 시각으로 준다")
    void getPackingSessionWhenNoItems() {
        // given
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        // when
        PackingProgressResponse response = tripService.getPackingSession(trip.getId(), 1L);

        // then
        assertThat(response.getPackedItemCount()).isZero();
        assertThat(response.getTotalItemCount()).isZero();
        assertThat(response.getLastUpdatedAt()).isEqualTo(trip.getCreatedAt());
    }

    @Test
    @DisplayName("존재하지 않는 여행의 짐싸기 진척도를 조회하면 예외가 발생한다")
    void getPackingSessionNotFound() {
        assertThatThrownBy(() -> tripService.getPackingSession(999L, 1L))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    @DisplayName("본인의 여행이 아니면 짐싸기 진척도를 조회할 수 없다")
    void getPackingSessionWithNotOwner() {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        assertThatThrownBy(() -> tripService.getPackingSession(trip.getId(), 2L))
                .isInstanceOf(TripAccessDeniedException.class);
    }

    private Trip createTrip(Long memberId, LocalDate startDate, LocalDate endDate) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .destination("오사카")
                .startDate(startDate)
                .endDate(endDate)
                .purposes(List.of("휴가"))
                .latitude(34.6937)
                .longitude(135.5023)
                .build());
    }
}
