package ssafy.checkmate.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
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
import ssafy.checkmate.domain.trip.dto.response.PlaceDetailResponse;
import ssafy.checkmate.domain.trip.dto.response.PlaceSuggestionResponse;
import ssafy.checkmate.domain.trip.dto.response.TripBagResponse;
import ssafy.checkmate.domain.trip.dto.response.TripCreateResponse;
import ssafy.checkmate.domain.trip.dto.response.TripDetailResponse;
import ssafy.checkmate.domain.trip.dto.response.TripListResponse;
import ssafy.checkmate.domain.trip.dto.response.UpcomingTripResponse;
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
import ssafy.checkmate.domain.weather.DailyWeather;
import ssafy.checkmate.domain.weather.WeatherClient;
import ssafy.checkmate.domain.weather.WeatherType;
import ssafy.checkmate.domain.weather.entity.TripWeather;
import ssafy.checkmate.domain.weather.repository.TripWeatherRepository;

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

    @Autowired
    private TripWeatherRepository tripWeatherRepository;

    @MockitoBean
    private GooglePlacesClient googlePlacesClient;

    @MockitoBean
    private WeatherClient weatherClient;

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
    @DisplayName("목적지 상세를 조회하면 이름과 좌표를 반환한다")
    void getDestinationDetails() {
        // given
        given(googlePlacesClient.getDetails("place-1", "session-1"))
                .willReturn(new PlaceDetails("도쿄", 35.6895, 139.69171));

        // when
        PlaceDetailResponse response = tripService.getDestinationDetails("place-1", "session-1");

        // then
        assertThat(response.getDestination()).isEqualTo("도쿄");
        assertThat(response.getLatitude()).isEqualTo(35.6895);
        assertThat(response.getLongitude()).isEqualTo(139.69171);
    }

    @Test
    @DisplayName("유효하지 않은 placeId로 목적지 상세를 조회하면 예외가 발생한다")
    void getDestinationDetailsWithInvalidPlace() {
        // given
        given(googlePlacesClient.getDetails("invalid-place", "session-1"))
                .willThrow(new TripInvalidDestinationException());

        // when // then
        assertThatThrownBy(() -> tripService.getDestinationDetails("invalid-place", "session-1"))
                .isInstanceOf(TripInvalidDestinationException.class)
                .hasMessage("유효하지 않은 목적지입니다.");
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
    @DisplayName("여행을 생성하면 16일 이내 예보 날씨가 함께 저장된다")
    void createTripSavesWeatherForecast() {
        // given
        given(googlePlacesClient.getDetails("place-1", "session-1"))
                .willReturn(new PlaceDetails("도쿄", 35.6895, 139.69171));
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(3);
        given(weatherClient.getForecast(anyDouble(), anyDouble(), any(), any())).willReturn(List.of(
                new DailyWeather(startDate, WeatherType.RAINY, 14.0, 18.0, 80)));
        TripCreateRequest request = tripCreateRequest("place-1", "session-1", startDate, endDate, List.of("관광"));

        // when
        TripCreateResponse response = tripService.createTrip(1L, request);

        // then
        List<TripWeather> savedWeathers = tripWeatherRepository.findAllByTripIdOrderByDateAsc(response.getTripId());
        assertThat(savedWeathers).hasSize(1);
        assertThat(savedWeathers.get(0).getDate()).isEqualTo(startDate);
        assertThat(savedWeathers.get(0).getWeatherType()).isEqualTo(WeatherType.RAINY);
        assertThat(savedWeathers.get(0).getMinTemperature()).isEqualTo(14.0);
        assertThat(savedWeathers.get(0).getMaxTemperature()).isEqualTo(18.0);
        assertThat(savedWeathers.get(0).getPrecipitationProbability()).isEqualTo(80);
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
    @DisplayName("지난 여행은 제외하고 담긴/체크된 물품 개수를 함께 반환한다")
    void getTrips() {
        // given
        LocalDate today = LocalDate.now();
        Trip currentTrip = createTrip(1L, today.plusDays(1), today.plusDays(3));
        Trip pastTrip = createTrip(1L, today.minusDays(10), today.minusDays(5));
        Bag defaultBag = bagRepository.save(Bag.createDefault(currentTrip.getId(), 1L));
        BagItem passport = bagItemRepository.save(
                BagItem.create(currentTrip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));
        bagItemRepository.save(BagItem.create(currentTrip.getId(), 1L, "우산", "생활용품", 1, defaultBag.getId()));
        passport.update(null, null, null, true, null);

        // when
        TripListResponse response = tripService.getTrips(1L, today);

        // then
        assertThat(response.getTrips()).hasSize(1)
                .extracting("tripId", "packedItemCount", "totalItemCount", "tripType")
                .containsExactly(tuple(currentTrip.getId(), 1, 2, TripType.UPCOMING));
    }

    @Test
    @DisplayName("여행 목록 조회 시 현재 준비 단계(2/4, 체크리스트 생성)를 함께 반환한다")
    void getTripsWithCurrentStep() {
        // given
        LocalDate today = LocalDate.now();
        createTrip(1L, today.plusDays(1), today.plusDays(3));

        // when
        TripListResponse response = tripService.getTrips(1L, today);

        // then
        assertThat(response.getTrips()).hasSize(1)
                .extracting("currentStep", "totalStep", "currentStepName")
                .containsExactly(tuple(2, 4, "체크리스트 생성"));
    }

    @Test
    @DisplayName("출발일이 오늘이거나 이전이면 여행 중, 이후면 여행 예정으로 분류한다")
    void getTripsClassifiesOngoingVsUpcoming() {
        // given
        LocalDate today = LocalDate.now();
        Trip ongoingTrip = createTrip(1L, today, today.plusDays(2));
        Trip upcomingTrip = createTrip(1L, today.plusDays(1), today.plusDays(3));

        // when
        TripListResponse response = tripService.getTrips(1L, today);

        // then
        assertThat(response.getTrips())
                .extracting("tripId", "tripType")
                .containsExactlyInAnyOrder(
                        tuple(ongoingTrip.getId(), TripType.ONGOING),
                        tuple(upcomingTrip.getId(), TripType.UPCOMING));
    }

    @Test
    @DisplayName("여행 목록은 종료일이 오늘에 가까운 순서로 정렬한다")
    void getTripsOrderedByEndDate() {
        // given
        LocalDate today = LocalDate.now();
        Trip laterTrip = createTrip(1L, today.plusDays(3), today.plusDays(4));
        Trip soonerTrip = createTrip(1L, today.plusDays(1), today.plusDays(2));

        // when
        TripListResponse response = tripService.getTrips(1L, today);

        // then
        assertThat(response.getTrips())
                .extracting("tripId")
                .containsExactly(soonerTrip.getId(), laterTrip.getId());
    }

    @Test
    @DisplayName("여행에 저장된 날짜별 날씨를 함께 반환한다")
    void getTripsWithWeather() {
        // given
        LocalDate today = LocalDate.now();
        Trip trip = createTrip(1L, today.plusDays(1), today.plusDays(2));
        tripWeatherRepository.save(TripWeather.of(trip.getId(),
                new DailyWeather(today.plusDays(1), WeatherType.SUNNY, 12.4, 21.0, 10)));

        // when
        TripListResponse response = tripService.getTrips(1L, today);

        // then
        assertThat(response.getTrips()).hasSize(1);
        assertThat(response.getTrips().get(0).getWeathers())
                .extracting("date", "weatherType", "minTemperature", "maxTemperature", "precipitationProbability")
                .containsExactly(tuple(today.plusDays(1), "맑음", 12.4, 21.0, 10));
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
                .containsExactly(TripType.ONGOING);
    }

    @Test
    @DisplayName("여행 상세를 조회하면 타입/기간/출국 항공사와 가방별 물품 목록을 반환한다")
    void getTripDetail() {
        // given
        LocalDate today = LocalDate.now();
        Trip trip = tripRepository.save(Trip.builder()
                .memberId(1L)
                .destination("오사카")
                .startDate(today.plusDays(1))
                .endDate(today.plusDays(3))
                .purposes(List.of("휴가"))
                .hasFlight(true)
                .departureAirline("대한항공")
                .arrivalAirline("피치항공")
                .latitude(34.6937)
                .longitude(135.5023)
                .build());
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        Bag carrier = bagRepository.save(Bag.create(trip.getId(), 1L, "캐리어"));
        bagRepository.save(Bag.create(trip.getId(), 1L, "백팩"));
        BagItem passport = bagItemRepository.save(
                BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));
        passport.update(null, null, null, true, null);
        bagItemRepository.save(BagItem.create(trip.getId(), 1L, "우산", "생활용품", 2, carrier.getId()));

        // when
        TripDetailResponse response = tripService.getTripDetail(trip.getId(), 1L, today);

        // then
        assertThat(response.getTripType()).isEqualTo(TripType.UPCOMING);
        assertThat(response.getDestination()).isEqualTo("오사카");
        assertThat(response.getStartDate()).isEqualTo(today.plusDays(1));
        assertThat(response.getEndDate()).isEqualTo(today.plusDays(3));
        assertThat(response.getDepartureAirline()).isEqualTo("대한항공");
        assertThat(response.getCurrentStep()).isEqualTo(2);
        assertThat(response.getTotalStep()).isEqualTo(4);
        assertThat(response.getCurrentStepName()).isEqualTo("체크리스트 생성");
        assertThat(response.getTotalItemCount()).isEqualTo(2);
        assertThat(response.getRecognizedItemCount()).isEqualTo(1);
        assertThat(response.getBags()).hasSize(3)
                .extracting("bagName")
                .containsExactlyInAnyOrder("NONE", "캐리어", "백팩");

        TripBagResponse carrierBag = response.getBags().stream()
                .filter(bag -> bag.getBagName().equals("캐리어")).findFirst().orElseThrow();
        assertThat(carrierBag.getItems()).hasSize(1)
                .extracting("name", "quantity")
                .containsExactly(tuple("우산", 2));

        TripBagResponse backpackBag = response.getBags().stream()
                .filter(bag -> bag.getBagName().equals("백팩")).findFirst().orElseThrow();
        assertThat(backpackBag.getItems()).isEmpty();
    }

    @Test
    @DisplayName("항공편을 이용하지 않으면 여행 상세의 항공사는 null이다")
    void getTripDetailWithoutFlight() {
        // given
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        // when
        TripDetailResponse response = tripService.getTripDetail(trip.getId(), 1L, LocalDate.now());

        // then
        assertThat(response.getDepartureAirline()).isNull();
        assertThat(response.getBags()).isEmpty();
        assertThat(response.getTotalItemCount()).isZero();
        assertThat(response.getRecognizedItemCount()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 여행의 상세를 조회하면 예외가 발생한다")
    void getTripDetailNotFound() {
        assertThatThrownBy(() -> tripService.getTripDetail(999L, 1L, LocalDate.now()))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    @DisplayName("본인의 여행이 아니면 상세를 조회할 수 없다")
    void getTripDetailWithNotOwner() {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        assertThatThrownBy(() -> tripService.getTripDetail(trip.getId(), 2L, LocalDate.now()))
                .isInstanceOf(TripAccessDeniedException.class);
    }

    @Test
    @DisplayName("다가오는 여행 조회 시 아직 출발하지 않은 여행 중 출발일이 가장 임박한 1건을 반환한다")
    void getUpcomingTrip() {
        // given
        LocalDate today = LocalDate.now();
        createTrip(1L, today.plusDays(10), today.plusDays(12));
        Trip soonest = createTrip(1L, today.plusDays(3), today.plusDays(5));

        // when
        UpcomingTripResponse response = tripService.getUpcomingTrip(1L, today);

        // then
        assertThat(response.getTripId()).isEqualTo(soonest.getId());
        assertThat(response.getDestination()).isEqualTo("오사카");
        assertThat(response.getDDay()).isEqualTo(3);
        assertThat(response.getPackedItemCount()).isZero();
        assertThat(response.getTotalItemCount()).isZero();
    }

    @Test
    @DisplayName("다가오는 여행 조회 시 담은/전체 물품 개수를 함께 반환한다")
    void getUpcomingTripWithItemCounts() {
        // given
        LocalDate today = LocalDate.now();
        Trip trip = createTrip(1L, today.plusDays(3), today.plusDays(5));
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        BagItem packed = bagItemRepository.save(
                BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));
        bagItemRepository.save(BagItem.create(trip.getId(), 1L, "우산", "생활용품", 1, defaultBag.getId()));
        packed.update(null, null, null, true, null);

        // when
        UpcomingTripResponse response = tripService.getUpcomingTrip(1L, today);

        // then
        assertThat(response.getPackedItemCount()).isEqualTo(1);
        assertThat(response.getTotalItemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("다가오는 여행 조회 시 출발일이 오늘인 여행도 포함하고 Dday는 0이다")
    void getUpcomingTripStartingToday() {
        // given
        LocalDate today = LocalDate.now();
        Trip trip = createTrip(1L, today, today.plusDays(2));

        // when
        UpcomingTripResponse response = tripService.getUpcomingTrip(1L, today);

        // then
        assertThat(response.getTripId()).isEqualTo(trip.getId());
        assertThat(response.getDDay()).isZero();
    }

    @Test
    @DisplayName("다가오는 여행 조회 시 이미 출발한(여행 중인) 여행은 제외한다")
    void getUpcomingTripExcludesOngoingTrip() {
        // given
        LocalDate today = LocalDate.now();
        createTrip(1L, today.minusDays(1), today.plusDays(2));

        // when
        UpcomingTripResponse response = tripService.getUpcomingTrip(1L, today);

        // then
        assertThat(response).isNull();
    }

    @Test
    @DisplayName("다가오는 여행이 없으면 null을 반환한다")
    void getUpcomingTripWhenNone() {
        // when
        UpcomingTripResponse response = tripService.getUpcomingTrip(1L, LocalDate.now());

        // then
        assertThat(response).isNull();
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
    @DisplayName("여행을 삭제하면 속한 가방과 체크리스트 항목, 날씨 데이터도 함께 삭제된다")
    void deleteTrip() {
        // given
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        Bag bag = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, bag.getId()));
        TripWeather weather = tripWeatherRepository.save(
                TripWeather.of(trip.getId(), new DailyWeather(trip.getStartDate(), WeatherType.SUNNY, 12.0, 20.0, 10)));

        // when
        tripService.deleteTrip(trip.getId(), 1L);

        // then
        assertThat(tripRepository.findById(trip.getId())).isEmpty();
        assertThat(bagRepository.findById(bag.getId())).isEmpty();
        assertThat(bagItemRepository.findById(item.getId())).isEmpty();
        assertThat(tripWeatherRepository.findById(weather.getId())).isEmpty();
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
