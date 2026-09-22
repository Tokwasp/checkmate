package ssafy.checkmate.domain.checklist.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.request.ReturnCheckItemCheckRequest;
import ssafy.checkmate.domain.checklist.dto.request.ReturnCheckScanRequest;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckBagResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckFoundItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckItemCheckResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckItemsResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckMissingItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckScanResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.exception.BagItemAccessDeniedException;
import ssafy.checkmate.domain.checklist.exception.BagItemNotFoundException;
import ssafy.checkmate.domain.checklist.exception.NotReturnCheckTargetException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;

/**
 * 귀가 전 점검(31·32·33번). 돌아오기 전에 두고 온 물건이 없는지 확인하는 화면들을 그린다.
 *
 * 짐 쌀 때 담은 물품(departureChecked)을 목록에 올리고 그 안에서 귀가 전 체크
 * (returnChecked)를 읽고 쓴다 — 애초에 안 담은 물건을 "두고 왔다"고 물을 수는 없다.
 * 두 컬럼은 서로 건드리지 않는다.
 *
 * <ul>
 *   <li>31번 — 가방별로 묶은 점검 목록 화면</li>
 *   <li>32번 — 카메라 화면의 '확인 / 안 보임' 두 탭</li>
 *   <li>33번 — 카메라가 알아본 이름으로 한 줄 체크</li>
 *   <li>33-2번 — itemId로 한 줄 체크·해제(손으로 누르기, 카메라가 켠 것 되돌리기)</li>
 * </ul>
 *
 * 점검 완료는 다음 티켓이다. 여기서는 어느 메서드도 여행 단계를 ④귀가 전 점검으로 올리지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReturnCheckService {

    private static final String ITEM_NOT_FOUND_MESSAGE = "존재하지 않는 물품입니다.";

    private final TripRepository tripRepository;
    private final BagRepository bagRepository;
    private final BagItemRepository bagItemRepository;

    public ReturnCheckResponse getReturnCheck(Long tripId, Long memberId) {
        Trip trip = findTripOwnedBy(tripId, memberId);

        // 가방마다 나눠 조회하지 않는다 — 어차피 물품 본문이 전부 필요해 한 번에 읽고 메모리에서 묶는다
        List<BagItem> packedItems =
                bagItemRepository.findAllByTripIdAndDepartureCheckedOrderByIdAsc(tripId, true);

        return ReturnCheckResponse.of(trip, groupByBag(packedItems, findBagsById(tripId)));
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

    /**
     * 32번. 카메라 화면 시트의 두 탭 — 확인된 물품과 아직 안 보인 물품.
     *
     * 가방으로 묶지 않는다(31번과 다른 점). 대신 두 목록 모두 줄마다 가방명을 단다 —
     * "어느 가방 것을 못 찾았나"가 이 화면의 핵심 정보다.
     */
    public ReturnCheckItemsResponse getItems(Long tripId, Long memberId) {
        findTripOwnedBy(tripId, memberId, "본인의 여행만 조회할 수 있습니다.");

        Map<Long, Bag> bagsById = findBagsById(tripId);
        List<ReturnCheckFoundItemResponse> checkedItems =
                bagItemRepository.findReturnCheckedItems(tripId).stream()
                        .map(item -> ReturnCheckFoundItemResponse.of(item, bagsById.get(item.getBagId())))
                        .toList();
        List<ReturnCheckMissingItemResponse> uncheckedItems =
                bagItemRepository.findReturnUncheckedItems(tripId).stream()
                        .map(item -> ReturnCheckMissingItemResponse.of(item, bagsById.get(item.getBagId())))
                        .toList();

        return ReturnCheckItemsResponse.of(checkedItems, uncheckedItems);
    }

    /**
     * 33번. 카메라가 알아본 이름으로 아직 확인 안 된 물품 하나를 찾아 체크한다.
     *
     * 못 찾아도 실패가 아니다 — 카메라가 2초마다 쏘는 화면이라 체크리스트에 없는 물건이
     * 자주 비친다. 같은 이름이 여럿이면 하나만 체크한다. 하나만 챙겼는데 전부 확인된 것으로
     * 남으면 안 되기 때문이다.
     */
    @Transactional
    public ReturnCheckScanResponse scan(Long tripId, Long memberId, ReturnCheckScanRequest request) {
        findTripOwnedBy(tripId, memberId, "본인의 여행만 수정할 수 있습니다.");

        Optional<BagItem> target = bagItemRepository
                .findReturnUncheckedItemsByName(tripId, request.getName()).stream()
                .findFirst();
        if (target.isEmpty()) {
            return ReturnCheckScanResponse.notMatched((int) bagItemRepository.countReturnUncheckedItems(tripId));
        }

        BagItem item = target.get();
        item.checkReturn();

        Bag bag = bagRepository.findById(item.getBagId()).orElse(null);
        return ReturnCheckScanResponse.matched(
                (int) bagItemRepository.countReturnUncheckedItems(tripId),
                ReturnCheckFoundItemResponse.of(item, bag));
    }

    /**
     * 33-2번. 물품 하나의 귀가 전 체크를 손으로 켜고 끈다.
     *
     * 33번과 달리 itemId로 직접 찾으므로 "못 찾음"이 없다 — 없는 물품·남의 물품·다른 여행의
     * 물품은 그대로 에러다. 바꿀 상태를 그대로 받기 때문에 <b>같은 값을 두 번 보내도 결과가
     * 같다</b>(33번은 부를 때마다 한 줄씩 켜는 것과 다르다).
     *
     * 짐 쌀 때 담지 않은 물품은 애초에 점검 대상이 아니라 400으로 거절한다 — 켜줘 봤자
     * 31·32번 목록에는 안 보이면서 개수만 어긋난다.
     */
    @Transactional
    public ReturnCheckItemCheckResponse checkItem(Long tripId, Long memberId, Long itemId,
            ReturnCheckItemCheckRequest request) {
        findTripOwnedBy(tripId, memberId, "본인의 여행만 수정할 수 있습니다.");

        BagItem item = findItemOf(tripId, itemId, memberId);
        if (!item.isDepartureChecked()) {
            throw new NotReturnCheckTargetException();
        }
        item.changeReturnChecked(request.getReturnChecked());

        Bag bag = bagRepository.findById(item.getBagId()).orElse(null);
        return ReturnCheckItemCheckResponse.of(
                (int) bagItemRepository.countReturnUncheckedItems(tripId),
                ReturnCheckFoundItemResponse.of(item, bag));
    }

    /**
     * 남의 물품이면 403, 다른 여행의 물품이면 404다. 여행이 다른 것은 "이 여행에는 없는 물품"이라
     * 숨기는 편이 맞고, 주인이 다른 것은 감추면 프론트가 제 화면 버그로 오해한다.
     */
    private BagItem findItemOf(Long tripId, Long itemId, Long memberId) {
        BagItem item = bagItemRepository.findById(itemId)
                .orElseThrow(() -> new BagItemNotFoundException(ITEM_NOT_FOUND_MESSAGE));
        if (!item.isOwnedBy(memberId)) {
            throw new BagItemAccessDeniedException("본인의 물품만 수정할 수 있습니다.");
        }
        if (!item.getTripId().equals(tripId)) {
            throw new BagItemNotFoundException(ITEM_NOT_FOUND_MESSAGE);
        }
        return item;
    }

    private Map<Long, Bag> findBagsById(Long tripId) {
        return bagRepository.findAllByTripId(tripId).stream()
                .collect(Collectors.toMap(Bag::getId, Function.identity()));
    }

    private Trip findTripOwnedBy(Long tripId, Long memberId) {
        return findTripOwnedBy(tripId, memberId, "본인의 여행만 조회할 수 있습니다.");
    }

    private Trip findTripOwnedBy(Long tripId, Long memberId, String accessDeniedMessage) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        if (!trip.isOwnedBy(memberId)) {
            throw new TripAccessDeniedException(accessDeniedMessage);
        }
        return trip;
    }
}
