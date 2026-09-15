package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static ssafy.checkmate.domain.checklist.TestRequestFactory.checklistItemCreateRequest;
import static ssafy.checkmate.domain.checklist.TestRequestFactory.checklistItemUpdateRequest;
import static ssafy.checkmate.domain.checklist.TestRequestFactory.checklistItemsCreateRequest;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.response.ChecklistItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.ChecklistItemsResponse;
import ssafy.checkmate.domain.checklist.dto.response.ChecklistResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.exception.BagItemAccessDeniedException;
import ssafy.checkmate.domain.checklist.exception.BagItemNotFoundException;
import ssafy.checkmate.domain.checklist.exception.BagNotFoundException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.service.ChecklistRecommendationEngine;
import ssafy.checkmate.domain.checklist.service.ChecklistService;
import ssafy.checkmate.domain.checklist.service.RecommendedItem;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.domain.weather.DailyWeather;
import ssafy.checkmate.domain.weather.WeatherClient;
import ssafy.checkmate.domain.weather.WeatherType;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChecklistServiceTest {

    @Autowired
    private ChecklistService checklistService;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Autowired
    private TripRepository tripRepository;

    @MockitoBean
    private WeatherClient weatherClient;

    @MockitoBean
    private ChecklistRecommendationEngine recommendationEngine;

    @Test
    @DisplayName("추천 항목을 여행의 기본 가방에 담아 저장한다")
    void recommend() {
        // given
        Trip trip = createTrip(1L);
        given(weatherClient.getForecast(anyString(), any(), any())).willReturn(List.of(
                new DailyWeather(trip.getStartDate(), WeatherType.RAINY, 14.0, 18.0, 80)));
        given(recommendationEngine.recommend(any(), any())).willReturn(List.of(
                new RecommendedItem("여권", "서류", 1),
                new RecommendedItem("우산", "생활용품", 1)));

        // when
        ChecklistItemsResponse response = checklistService.recommend(trip.getId(), 1L);

        // then
        assertThat(response.getItems()).hasSize(2)
                .extracting(ChecklistItemResponse::getName)
                .containsExactlyInAnyOrder("여권", "우산");
        assertThat(response.getItems()).allSatisfy(item -> {
            assertThat(item.isChecked()).isFalse();
            assertThat(item.getBagName()).isEqualTo("NONE");
            assertThat(item.getCarriageType()).isEqualTo("NONE");
        });
        assertThat(bagItemRepository.findAllByTripId(trip.getId())).hasSize(2);
        assertThat(bagRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 여행을 추천 대상으로 하면 예외가 발생한다")
    void recommendWithNoTrip() {
        assertThatThrownBy(() -> checklistService.recommend(999L, 1L))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    @DisplayName("본인의 여행이 아니면 추천을 요청할 수 없다")
    void recommendWithNotOwner() {
        Trip trip = createTrip(1L);
        assertThatThrownBy(() -> checklistService.recommend(trip.getId(), 2L))
                .isInstanceOf(TripAccessDeniedException.class);
    }

    @Test
    @DisplayName("체크리스트 조회 시 날씨와 항목을 함께 반환한다")
    void getChecklist() {
        // given
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        Bag carrier = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));
        bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));
        bagItemRepository.save(BagItem.create(trip.getId(), 1L, "우산", "생활용품", 1, carrier.getId()));
        given(weatherClient.getForecast(anyString(), any(), any())).willReturn(List.of(
                new DailyWeather(trip.getStartDate(), WeatherType.SUNNY, 12.0, 21.0, 10)));

        // when
        ChecklistResponse response = checklistService.getChecklist(trip.getId(), 1L);

        // then
        assertThat(response.getStartDate()).isEqualTo(trip.getStartDate());
        assertThat(response.getWeathers()).hasSize(1);
        assertThat(response.getItems()).hasSize(2)
                .extracting(ChecklistItemResponse::getBagName)
                .containsExactlyInAnyOrder("NONE", "내 캐리어");
    }

    @Test
    @DisplayName("본인의 여행이 아니면 체크리스트를 조회할 수 없다")
    void getChecklistWithNotOwner() {
        Trip trip = createTrip(1L);
        assertThatThrownBy(() -> checklistService.getChecklist(trip.getId(), 2L))
                .isInstanceOf(TripAccessDeniedException.class);
    }

    @Test
    @DisplayName("가방을 지정하지 않은 항목은 기본 가방에 담기고, 지정한 항목은 그 가방에 담긴다")
    void addItems() {
        // given
        Trip trip = createTrip(1L);
        Bag carrier = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));

        // when
        ChecklistItemsResponse response = checklistService.addItems(trip.getId(), 1L, checklistItemsCreateRequest(List.of(
                checklistItemCreateRequest("보조배터리", "전자기기", 2, carrier.getId()),
                checklistItemCreateRequest("선크림", "생활용품", 1, null))));

        // then
        assertThat(response.getItems()).hasSize(2);
        ChecklistItemResponse withBag = response.getItems().stream()
                .filter(i -> i.getName().equals("보조배터리")).findFirst().orElseThrow();
        ChecklistItemResponse withoutBag = response.getItems().stream()
                .filter(i -> i.getName().equals("선크림")).findFirst().orElseThrow();
        assertThat(withBag.getBagName()).isEqualTo("내 캐리어");
        assertThat(withoutBag.getBagName()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("존재하지 않는 여행에 항목을 추가하면 예외가 발생한다")
    void addItemsWithNoTrip() {
        assertThatThrownBy(() -> checklistService.addItems(999L, 1L,
                checklistItemsCreateRequest(List.of(checklistItemCreateRequest("여권", "서류", 1, null)))))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    @DisplayName("본인의 여행이 아니면 항목을 추가할 수 없다")
    void addItemsWithNotOwner() {
        Trip trip = createTrip(1L);
        assertThatThrownBy(() -> checklistService.addItems(trip.getId(), 2L,
                checklistItemsCreateRequest(List.of(checklistItemCreateRequest("여권", "서류", 1, null)))))
                .isInstanceOf(TripAccessDeniedException.class);
    }

    @Test
    @DisplayName("다른 여행의 가방을 지정하면 예외가 발생한다")
    void addItemsWithBagFromAnotherTrip() {
        Trip trip = createTrip(1L);
        Trip otherTrip = createTrip(1L);
        Bag otherBag = bagRepository.save(Bag.create(otherTrip.getId(), 1L, "다른 여행 가방"));

        assertThatThrownBy(() -> checklistService.addItems(trip.getId(), 1L,
                checklistItemsCreateRequest(List.of(checklistItemCreateRequest("여권", "서류", 1, otherBag.getId())))))
                .isInstanceOf(BagNotFoundException.class);
    }

    @Test
    @DisplayName("전달된 필드만 반영해 항목을 수정한다")
    void updateItem() {
        // given
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        Bag carrier = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "보조배터리", "전자기기", 2, defaultBag.getId()));

        // when
        ChecklistItemResponse response = checklistService.updateItem(item.getId(), 1L,
                checklistItemUpdateRequest(null, 1, null, true, carrier.getId()));

        // then
        assertThat(response.getQuantity()).isEqualTo(1);
        assertThat(response.isChecked()).isTrue();
        assertThat(response.getBagName()).isEqualTo("내 캐리어");
        assertThat(response.getName()).isEqualTo("보조배터리");
        assertThat(response.getCategory()).isEqualTo("전자기기");
    }

    @Test
    @DisplayName("체크리스트 항목이 없으면 예외가 발생한다")
    void updateItemNotFound() {
        assertThatThrownBy(() -> checklistService.updateItem(999L, 1L,
                checklistItemUpdateRequest(null, null, null, true, null)))
                .isInstanceOf(BagItemNotFoundException.class);
    }

    @Test
    @DisplayName("본인의 체크리스트가 아니면 수정할 수 없다")
    void updateItemWithNotOwner() {
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));

        assertThatThrownBy(() -> checklistService.updateItem(item.getId(), 2L,
                checklistItemUpdateRequest(null, null, null, true, null)))
                .isInstanceOf(BagItemAccessDeniedException.class);
    }

    @Test
    @DisplayName("체크리스트 항목을 삭제한다")
    void deleteItem() {
        // given
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));

        // when
        checklistService.deleteItem(item.getId(), 1L);

        // then
        assertThat(bagItemRepository.findById(item.getId())).isEmpty();
    }

    @Test
    @DisplayName("본인의 체크리스트가 아니면 삭제할 수 없다")
    void deleteItemWithNotOwner() {
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, defaultBag.getId()));

        assertThatThrownBy(() -> checklistService.deleteItem(item.getId(), 2L))
                .isInstanceOf(BagItemAccessDeniedException.class);
    }

    private Trip createTrip(Long memberId) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .destination("오사카")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .purposes(List.of("휴가"))
                .build());
    }
}
