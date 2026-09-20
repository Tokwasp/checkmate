package ssafy.checkmate.domain.checklist.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckBagResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;

/**
 * 귀가 전 점검(31번). 돌아오기 전에 두고 온 물건이 없는지 확인하는 화면을 그린다.
 *
 * 짐 쌀 때 담은 물품(departureChecked)을 목록에 올리고 그 안에서 귀가 전 체크
 * (returnChecked)를 읽는다 — 애초에 안 담은 물건을 "두고 왔다"고 물을 수는 없다.
 * 두 컬럼은 서로 건드리지 않는다.
 *
 * 체크·완료 API는 다음 티켓이라 이 서비스는 아직 읽기만 한다 — 조회로 여행 단계를
 * ④귀가 전 점검으로 올리지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReturnCheckService {

    private final TripRepository tripRepository;
    private final BagRepository bagRepository;
    private final BagItemRepository bagItemRepository;

    public ReturnCheckResponse getReturnCheck(Long tripId, Long memberId) {
        Trip trip = findTripOwnedBy(tripId, memberId);

        // 가방마다 나눠 조회하지 않는다 — 어차피 물품 본문이 전부 필요해 한 번에 읽고 메모리에서 묶는다
        List<BagItem> packedItems =
                bagItemRepository.findAllByTripIdAndDepartureCheckedOrderByIdAsc(tripId, true);
        Map<Long, Bag> bagsById = bagRepository.findAllByTripId(tripId).stream()
                .collect(Collectors.toMap(Bag::getId, Function.identity()));

        return ReturnCheckResponse.of(trip, groupByBag(packedItems, bagsById));
    }

    /**
     * 맨 앞에 전 물품을 담은 합산 가방 '전체'를 두고, 물품이 담긴 가방만 bagId 오름차순으로 잇는다.
     * 담긴 게 없는 가방은 누를 게 없는 칩이라 내리지 않는다. '전체'는 0개여도 항상 남긴다.
     */
    private List<ReturnCheckBagResponse> groupByBag(List<BagItem> packedItems, Map<Long, Bag> bagsById) {
        Map<Long, List<ReturnCheckItemResponse>> itemsByBagId = new LinkedHashMap<>();
        List<ReturnCheckItemResponse> allItems = new ArrayList<>();

        for (BagItem item : packedItems) {
            ReturnCheckItemResponse response = ReturnCheckItemResponse.of(item, bagsById.get(item.getBagId()));
            allItems.add(response);
            itemsByBagId.computeIfAbsent(item.getBagId(), id -> new ArrayList<>()).add(response);
        }

        List<ReturnCheckBagResponse> bags = new ArrayList<>();
        bags.add(ReturnCheckBagResponse.ofAll(allItems));
        itemsByBagId.keySet().stream()
                .filter(bagsById::containsKey)
                .sorted()
                .forEach(bagId -> bags.add(
                        ReturnCheckBagResponse.of(bagsById.get(bagId), itemsByBagId.get(bagId))));
        return bags;
    }

    private Trip findTripOwnedBy(Long tripId, Long memberId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        if (!trip.isOwnedBy(memberId)) {
            throw new TripAccessDeniedException("본인의 여행만 조회할 수 있습니다.");
        }
        return trip;
    }
}
