package ssafy.checkmate.domain.checklist;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ssafy.checkmate.domain.checklist.service.ChecklistRecommendationEngine;
import ssafy.checkmate.domain.checklist.service.RecommendedItem;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.domain.weather.WeatherClient;
import ssafy.checkmate.global.auth.JwtTokenProvider;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChecklistControllerTest {

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
    private WeatherClient weatherClient;

    @MockitoBean
    private ChecklistRecommendationEngine recommendationEngine;

    @Test
    @DisplayName("체크리스트 추천을 생성한다")
    void recommend() throws Exception {
        Trip trip = createTrip(1L);
        given(weatherClient.getForecast(anyDouble(), anyDouble(), any(), any())).willReturn(List.of());
        given(recommendationEngine.recommend(any(), any())).willReturn(List.of(new RecommendedItem("여권", "서류", 1)));

        mockMvc.perform(post("/api/v1/trips/{tripId}/checklists/recommend", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("여권"))
                .andExpect(jsonPath("$.data.items[0].checked").value(false))
                .andExpect(jsonPath("$.data.items[0].bagName").value("NONE"));
    }

    @Test
    @DisplayName("체크리스트를 조회하면 날씨와 항목을 함께 응답한다")
    void getChecklist() throws Exception {
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));
        given(weatherClient.getForecast(anyDouble(), anyDouble(), any(), any())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/trips/{tripId}/checklists", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("여권"));
    }

    @Test
    @DisplayName("체크리스트 항목을 배열로 한 번에 추가한다")
    void addItems() throws Exception {
        Trip trip = createTrip(1L);

        mockMvc.perform(post("/api/v1/trips/{tripId}/checklists/items", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"name":"보조배터리","category":"전자기기","quantity":2}]}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("보조배터리"))
                .andExpect(jsonPath("$.data.items[0].bagName").value("NONE"));
    }

    @Test
    @DisplayName("추가할 항목이 없으면 400을 응답한다")
    void addItemsWithEmptyList() throws Exception {
        Trip trip = createTrip(1L);

        mockMvc.perform(post("/api/v1/trips/{tripId}/checklists/items", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("추가할 항목이 없습니다."));
    }

    @Test
    @DisplayName("항목 이름이 없으면 400을 응답한다")
    void addItemsWithoutName() throws Exception {
        Trip trip = createTrip(1L);

        mockMvc.perform(post("/api/v1/trips/{tripId}/checklists/items", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"category\":\"전자기기\",\"quantity\":1}]}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("항목 이름이 누락되었습니다."));
    }

    @Test
    @DisplayName("체크리스트 항목을 수정한다")
    void updateItem() throws Exception {
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "보조배터리", "전자기기", 2, defaultBag.getId()));

        mockMvc.perform(patch("/api/v1/checklists/items/{itemId}", item.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checked\":true}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checked").value(true));
    }

    @Test
    @DisplayName("존재하지 않는 체크리스트 항목을 수정하면 404를 응답한다")
    void updateItemNotFound() throws Exception {
        mockMvc.perform(patch("/api/v1/checklists/items/{itemId}", 999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checked\":true}"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 체크리스트 항목입니다."));
    }

    @Test
    @DisplayName("체크리스트 항목을 삭제한다")
    void deleteItem() throws Exception {
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));

        mockMvc.perform(delete("/api/v1/checklists/items/{itemId}", item.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private Trip createTrip(Long memberId) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .country("일본")
                .region("오사카")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .purposes(List.of("휴가"))
                .latitude(34.6937)
                .longitude(135.5023)
                .build());
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
