package ssafy.checkmate.domain.checklist.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.request.ChecklistItemCreateRequest;
import ssafy.checkmate.domain.checklist.dto.request.ChecklistItemUpdateRequest;
import ssafy.checkmate.domain.checklist.dto.request.ChecklistItemsCreateRequest;
import ssafy.checkmate.domain.checklist.dto.response.ChecklistItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.ChecklistItemsResponse;
import ssafy.checkmate.domain.checklist.dto.response.ChecklistResponse;
import ssafy.checkmate.domain.checklist.dto.response.WeatherResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.exception.BagItemAccessDeniedException;
import ssafy.checkmate.domain.checklist.exception.BagItemNotFoundException;
import ssafy.checkmate.domain.checklist.exception.BagNotFoundException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.entity.TripStep;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.domain.weather.DailyWeather;
import ssafy.checkmate.domain.weather.entity.TripWeather;
import ssafy.checkmate.domain.weather.repository.TripWeatherRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    private final BagItemRepository bagItemRepository;
    private final BagRepository bagRepository;
    private final TripRepository tripRepository;
    private final BagService bagService;
    private final TripWeatherRepository tripWeatherRepository;
    private final ChecklistRecommendationEngine recommendationEngine;

    @Transactional
    public ChecklistItemsResponse recommend(Long tripId, Long memberId) {
        Trip trip = findTripOwnedBy(tripId, memberId, "본인의 여행만 조회할 수 있습니다.");

        List<DailyWeather> forecast = findForecast(tripId);
        List<RecommendedItem> recommended = recommendationEngine.recommend(trip, forecast);
        Bag defaultBag = bagService.ensureDefaultBag(tripId, memberId);

        List<BagItem> savedItems = recommended.stream()
                .map(item -> bagItemRepository.save(BagItem.create(
                        tripId, memberId, item.getName(), item.getCategory(), item.getQuantity(), defaultBag.getId())))
                .toList();

        if (!savedItems.isEmpty()) {
            trip.advanceStepAtLeast(TripStep.ITEM_RECOGNITION);
        }

        List<ChecklistItemResponse> items = savedItems.stream()
                .map(item -> ChecklistItemResponse.of(item, defaultBag))
                .toList();
        return ChecklistItemsResponse.of(items);
    }

    public ChecklistResponse getChecklist(Long tripId, Long memberId) {
        Trip trip = findTripOwnedBy(tripId, memberId, "본인의 여행만 조회할 수 있습니다.");

        List<WeatherResponse> weathers = findForecast(tripId).stream()
                .map(WeatherResponse::of)
                .toList();

        List<BagItem> bagItems = bagItemRepository.findAllByTripId(tripId);
        Map<Long, Bag> bagsById = findBagsByIds(bagItems.stream().map(BagItem::getBagId));

        List<ChecklistItemResponse> items = bagItems.stream()
                .map(item -> ChecklistItemResponse.of(item, bagsById.get(item.getBagId())))
                .toList();

        return ChecklistResponse.of(trip.getStartDate(), trip.getEndDate(), weathers, items);
    }

    @Transactional
    public ChecklistItemsResponse addItems(Long tripId, Long memberId, ChecklistItemsCreateRequest request) {
        Trip trip = findTripOwnedBy(tripId, memberId, "본인의 여행만 수정할 수 있습니다.");

        List<ChecklistItemCreateRequest> requestedItems = request.getItems();
        Set<Long> requestedBagIds = requestedItems.stream()
                .map(ChecklistItemCreateRequest::getBagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Bag> requestedBagsById = findBagsByIds(requestedBagIds.stream());

        boolean allBelongToTrip = requestedBagsById.values().stream()
                .allMatch(bag -> bag.getTripId().equals(tripId));

        if (requestedBagsById.size() != requestedBagIds.size() || !allBelongToTrip) {
            throw new BagNotFoundException();
        }

        Bag defaultBag = bagService.ensureDefaultBag(tripId, memberId);
        Map<Long, Bag> bagsById = new HashMap<>(requestedBagsById);
        bagsById.put(defaultBag.getId(), defaultBag);

        List<BagItem> savedItems = requestedItems.stream()
                .map(item -> {
                    Long bagId = item.getBagId() != null ? item.getBagId() : defaultBag.getId();
                    return bagItemRepository.save(BagItem.create(
                            tripId, memberId, item.getName(), item.getCategory(), item.getQuantity(), bagId));
                })
                .toList();

        if (!savedItems.isEmpty()) {
            trip.advanceStepAtLeast(TripStep.ITEM_RECOGNITION);
        }

        List<ChecklistItemResponse> items = savedItems.stream()
                .map(item -> ChecklistItemResponse.of(item, bagsById.get(item.getBagId())))
                .toList();
        return ChecklistItemsResponse.of(items);
    }

    @Transactional
    public ChecklistItemResponse updateItem(Long itemId, Long memberId, ChecklistItemUpdateRequest request) {
        BagItem item = bagItemRepository.findById(itemId).orElseThrow(BagItemNotFoundException::new);
        if (!item.isOwnedBy(memberId)) {
            throw new BagItemAccessDeniedException("본인의 체크리스트만 수정할 수 있습니다.");
        }

        Long targetBagId = request.getBagId() != null ? request.getBagId() : item.getBagId();
        Bag bag = bagRepository.findById(targetBagId).orElseThrow(BagNotFoundException::new);
        if (!bag.getTripId().equals(item.getTripId())) {
            throw new BagNotFoundException();
        }

        item.update(request.getName(), request.getQuantity(), request.getCategory(), request.getChecked(), request.getBagId());
        return ChecklistItemResponse.of(item, bag);
    }

    @Transactional
    public void deleteItem(Long itemId, Long memberId) {
        BagItem item = bagItemRepository.findById(itemId).orElseThrow(BagItemNotFoundException::new);
        if (!item.isOwnedBy(memberId)) {
            throw new BagItemAccessDeniedException("본인의 체크리스트만 삭제할 수 있습니다.");
        }
        // 인벤토리 사진 삭제는 no-op(image_url이 애초에 항상 null, docs.md §0/§2-1-1, 별도 티켓)
        bagItemRepository.delete(item);
    }

    private List<DailyWeather> findForecast(Long tripId) {
        return tripWeatherRepository.findAllByTripIdOrderByDateAsc(tripId).stream()
                .map(TripWeather::toDailyWeather)
                .toList();
    }

    private Trip findTripOwnedBy(Long tripId, Long memberId, String accessDeniedMessage) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        if (!trip.isOwnedBy(memberId)) {
            throw new TripAccessDeniedException(accessDeniedMessage);
        }
        return trip;
    }

    private Map<Long, Bag> findBagsByIds(Stream<Long> bagIds) {
        List<Long> ids = bagIds.distinct().toList();
        return bagRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Bag::getId, Function.identity()));
    }
}
