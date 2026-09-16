package ssafy.checkmate.domain.trip;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripInvalidDestinationException;
import ssafy.checkmate.domain.trip.place.GooglePlacesClient;
import ssafy.checkmate.domain.trip.place.PlaceDetails;
import ssafy.checkmate.domain.trip.place.PlaceSuggestion;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.auth.JwtTokenProvider;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private GooglePlacesClient googlePlacesClient;

    @Test
    @DisplayName("목적지를 검색하면 200과 후보 목록을 응답한다")
    void autocompleteDestination() throws Exception {
        given(googlePlacesClient.autocomplete("도쿄", "session-1"))
                .willReturn(List.of(new PlaceSuggestion("place-1", "일본", "도쿄")));

        mockMvc.perform(get("/api/v1/trips/destinations/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .param("query", "도쿄")
                        .param("sessionToken", "session-1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].placeId").value("place-1"))
                .andExpect(jsonPath("$.data[0].country").value("일본"))
                .andExpect(jsonPath("$.data[0].region").value("도쿄"));
    }

    @Test
    @DisplayName("목적지 상세를 조회하면 200과 좌표를 응답한다")
    void destinationDetails() throws Exception {
        given(googlePlacesClient.getDetails("place-1", "session-1"))
                .willReturn(new PlaceDetails(35.6895, 139.69171));

        mockMvc.perform(get("/api/v1/trips/destinations/details")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .param("placeId", "place-1")
                        .param("sessionToken", "session-1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(35.6895))
                .andExpect(jsonPath("$.data.longitude").value(139.69171));
    }

    @Test
    @DisplayName("유효하지 않은 placeId로 목적지 상세를 조회하면 400을 응답한다")
    void destinationDetailsWithInvalidPlace() throws Exception {
        given(googlePlacesClient.getDetails("invalid-place", "session-1"))
                .willThrow(new TripInvalidDestinationException());

        mockMvc.perform(get("/api/v1/trips/destinations/details")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .param("placeId", "invalid-place")
                        .param("sessionToken", "session-1"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("유효하지 않은 목적지입니다."));
    }

    @Test
    @DisplayName("여행을 생성하면 200과 여행 ID, 기본 가방 ID를 응답한다")
    void createTrip() throws Exception {
        given(googlePlacesClient.getDetails("place-1", "session-1"))
                .willReturn(new PlaceDetails(35.6895, 139.69171));

        mockMvc.perform(post("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":"place-1","sessionToken":"session-1",
                                 "country":"일본","region":"도쿄",
                                 "startDate":"2026-10-03","endDate":"2026-10-07",
                                 "purposes":["관광","미식"],"hasFlight":false}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.tripId").exists())
                .andExpect(jsonPath("$.data.defaultBagId").exists());
    }

    @Test
    @DisplayName("여행 생성 시 목적지(placeId)가 없으면 400을 응답한다")
    void createTripWithoutPlaceId() throws Exception {
        mockMvc.perform(post("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionToken":"session-1","country":"일본","region":"도쿄",
                                 "startDate":"2026-10-03",
                                 "endDate":"2026-10-07","hasFlight":false}
                                """))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("필수 여행 정보가 누락되었습니다."));
    }

    @Test
    @DisplayName("여행 생성 시 시작일이 없으면 400을 응답한다")
    void createTripWithoutStartDate() throws Exception {
        mockMvc.perform(post("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":"place-1","sessionToken":"session-1",
                                 "country":"일본","region":"도쿄",
                                 "endDate":"2026-10-07","hasFlight":false}
                                """))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("필수 여행 정보가 누락되었습니다."));
    }

    @Test
    @DisplayName("여행 생성 시 항공편 이용 여부가 없으면 400을 응답한다")
    void createTripWithoutHasFlight() throws Exception {
        mockMvc.perform(post("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":"place-1","sessionToken":"session-1",
                                 "country":"일본","region":"도쿄",
                                 "startDate":"2026-10-03","endDate":"2026-10-07"}
                                """))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("필수 여행 정보가 누락되었습니다."));
    }

    @Test
    @DisplayName("항공편을 이용하는데 항공사 정보가 없으면 400을 응답한다")
    void createTripWithFlightWithoutAirline() throws Exception {
        mockMvc.perform(post("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":"place-1","sessionToken":"session-1",
                                 "country":"일본","region":"도쿄",
                                 "startDate":"2026-10-03","endDate":"2026-10-07","hasFlight":true}
                                """))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("필수 여행 정보가 누락되었습니다."));
    }

    @Test
    @DisplayName("유효하지 않은 목적지로 여행을 생성하면 400을 응답한다")
    void createTripWithInvalidPlace() throws Exception {
        given(googlePlacesClient.getDetails("invalid-place", "session-1"))
                .willThrow(new TripInvalidDestinationException());

        mockMvc.perform(post("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":"invalid-place","sessionToken":"session-1",
                                 "country":"일본","region":"도쿄",
                                 "startDate":"2026-10-03","endDate":"2026-10-07","hasFlight":false}
                                """))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("유효하지 않은 목적지입니다."));
    }

    @Test
    @DisplayName("여행 목록을 조회한다")
    void getTrips() throws Exception {
        createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        mockMvc.perform(get("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trips.length()").value(1))
                .andExpect(jsonPath("$.data.trips[0].tripType").value("여행 예정"))
                .andExpect(jsonPath("$.data.trips[0].currentStep").value(2))
                .andExpect(jsonPath("$.data.trips[0].totalStep").value(4))
                .andExpect(jsonPath("$.data.trips[0].currentStepName").value("체크리스트 생성"));
    }

    @Test
    @DisplayName("출발일이 오늘이거나 이전이면 여행 중으로 표시한다")
    void getTripsShowsOngoingType() throws Exception {
        createTrip(1L, LocalDate.now(), LocalDate.now().plusDays(2));

        mockMvc.perform(get("/api/v1/trips")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trips[0].tripType").value("여행 중"));
    }

    @Test
    @DisplayName("여행 상세를 조회한다")
    void getTripDetail() throws Exception {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        Bag carrier = bagRepository.save(Bag.create(trip.getId(), 1L, "캐리어"));
        bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));
        bagItemRepository.save(BagItem.create(trip.getId(), 1L, "우산", "생활용품", 2, carrier.getId()));

        mockMvc.perform(get("/api/v1/trips/{tripId}", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripType").value("여행 예정"))
                .andExpect(jsonPath("$.data.country").value("일본"))
                .andExpect(jsonPath("$.data.region").value("오사카"))
                .andExpect(jsonPath("$.data.currentStep").value(2))
                .andExpect(jsonPath("$.data.totalStep").value(4))
                .andExpect(jsonPath("$.data.currentStepName").value("체크리스트 생성"))
                .andExpect(jsonPath("$.data.totalItemCount").value(2))
                .andExpect(jsonPath("$.data.recognizedItemCount").value(0))
                .andExpect(jsonPath("$.data.bags.length()").value(2))
                .andExpect(jsonPath("$.data.bags[*].bagName").value(org.hamcrest.Matchers.containsInAnyOrder("NONE", "캐리어")))
                .andExpect(jsonPath("$.data.bags[?(@.bagName == '캐리어')].items[0].name")
                        .value(org.hamcrest.Matchers.contains("우산")));
    }

    @Test
    @DisplayName("존재하지 않는 여행의 상세를 조회하면 404를 응답한다")
    void getTripDetailNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/trips/{tripId}", 999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("본인의 여행이 아니면 상세를 조회할 수 없다")
    void getTripDetailWithNotOwner() throws Exception {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        mockMvc.perform(get("/api/v1/trips/{tripId}", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("다가오는 여행을 조회한다")
    void getUpcomingTrip() throws Exception {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7));
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));
        item.update(null, null, null, true, null);
        bagItemRepository.save(BagItem.create(trip.getId(), 1L, "우산", "생활용품", 1, defaultBag.getId()));

        mockMvc.perform(get("/api/v1/trips/upcoming")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.country").value("일본"))
                .andExpect(jsonPath("$.data.region").value("오사카"))
                .andExpect(jsonPath("$.data.dDay").value(5))
                .andExpect(jsonPath("$.data.packedItemCount").value(1))
                .andExpect(jsonPath("$.data.totalItemCount").value(2));
    }

    @Test
    @DisplayName("다가오는 여행이 없으면 data는 null이다")
    void getUpcomingTripWhenNone() throws Exception {
        mockMvc.perform(get("/api/v1/trips/upcoming")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("여행 정보를 수정한다")
    void updateTrip() throws Exception {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        given(googlePlacesClient.getDetails("place-2", "session-2"))
                .willReturn(new PlaceDetails(34.6937, 135.5023));

        mockMvc.perform(put("/api/v1/trips/{tripId}", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":"place-2","sessionToken":"session-2",
                                 "country":"일본","region":"오사카",
                                 "startDate":"2026-10-03","endDate":"2026-10-08",
                                 "purposes":["관광"],"hasFlight":false}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 여행을 수정하면 404를 응답한다")
    void updateTripNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/trips/{tripId}", 999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":"place-1","sessionToken":"session-1",
                                 "country":"일본","region":"도쿄",
                                 "startDate":"2026-10-03","endDate":"2026-10-07","hasFlight":false}
                                """))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 여행입니다."));
    }

    @Test
    @DisplayName("본인의 여행이 아니면 수정할 수 없다")
    void updateTripWithNotOwner() throws Exception {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        mockMvc.perform(put("/api/v1/trips/{tripId}", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":"place-1","sessionToken":"session-1",
                                 "country":"일본","region":"도쿄",
                                 "startDate":"2026-10-03","endDate":"2026-10-07","hasFlight":false}
                                """))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("여행을 삭제한다")
    void deleteTrip() throws Exception {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        mockMvc.perform(delete("/api/v1/trips/{tripId}", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("짐싸기 진척도를 조회한다")
    void getPackingSession() throws Exception {
        Trip trip = createTrip(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));
        item.update(null, null, null, true, null);

        mockMvc.perform(get("/api/v1/trips/{tripId}/packing-session", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packedItemCount").value(1))
                .andExpect(jsonPath("$.data.totalItemCount").value(1));
    }

    private Trip createTrip(Long memberId, LocalDate startDate, LocalDate endDate) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .country("일본")
                .region("오사카")
                .startDate(startDate)
                .endDate(endDate)
                .purposes(List.of("휴가"))
                .latitude(34.6937)
                .longitude(135.5023)
                .build());
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
