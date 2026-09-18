package ssafy.checkmate.domain.checklist.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.request.BagItemUpdateRequest;
import ssafy.checkmate.domain.checklist.dto.response.InventoryItemsDeleteResponse;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.exception.BagItemAccessDeniedException;
import ssafy.checkmate.domain.checklist.exception.BagItemBagMismatchException;
import ssafy.checkmate.domain.checklist.exception.BagItemDeleteEmptyException;
import ssafy.checkmate.domain.checklist.exception.BagItemNotFoundException;
import ssafy.checkmate.domain.checklist.exception.BagItemUpdateEmptyException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagItemRiskLevelRepository;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;

/**
 * 짐 싸기(물건 인식) 27·28·29번. 가방에 담긴 물품의 수량·체크 여부를 수정하고 삭제한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private static final String ITEM_NOT_FOUND_MESSAGE = "존재하지 않는 인벤토리 물품입니다.";

    private final BagItemRepository bagItemRepository;
    private final BagItemRiskLevelRepository bagItemRiskLevelRepository;
    private final TripRepository tripRepository;

    @Transactional
    public void updateItem(Long itemId, Long memberId, BagItemUpdateRequest request) {
        if (request.getQuantity() == null && request.getChecked() == null) {
            throw new BagItemUpdateEmptyException();
        }

        BagItem item = findItemOwnedBy(itemId, memberId, "본인의 인벤토리만 수정할 수 있습니다.");
        if (!item.getBagId().equals(request.getBagId()) || !item.getName().equals(request.getName())) {
            throw new BagItemBagMismatchException();
        }

        item.updatePacking(request.getQuantity(), request.getChecked());
    }

    @Transactional
    public void deleteItem(Long itemId, Long memberId) {
        BagItem item = findItemOwnedBy(itemId, memberId, "본인의 인벤토리만 삭제할 수 있습니다.");

        // 물품과 체크리스트 항목이 같은 행이라 이 삭제로 체크리스트에서도 함께 사라진다
        bagItemRiskLevelRepository.deleteByBagItemId(item.getId());
        bagItemRepository.delete(item);
    }

    /**
     * 29번. 인벤토리 화면에서 선택한 물품 여러 개를 한 번에 삭제한다.
     * 하나라도 없거나·본인 것이 아니거나·다른 여행 소속이면 전체를 거절한다(부분 삭제 없음).
     * 위험도 행은 항공사 등 기준별로 물품 하나에 여러 개가 있을 수 있으므로 bagItemId 기준으로 전부 지운다.
     */
    @Transactional
    public InventoryItemsDeleteResponse deleteItems(Long tripId, Long memberId, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BagItemDeleteEmptyException();
        }
        findTripOwnedBy(tripId, memberId);

        List<Long> distinctIds = itemIds.stream().distinct().toList();
        List<BagItem> items = bagItemRepository.findAllById(distinctIds);
        if (items.size() != distinctIds.size()) {
            throw new BagItemNotFoundException(ITEM_NOT_FOUND_MESSAGE);
        }
        if (items.stream().anyMatch(item -> !item.isOwnedBy(memberId))) {
            throw new BagItemAccessDeniedException("본인의 인벤토리만 삭제할 수 있습니다.");
        }
        if (items.stream().anyMatch(item -> !item.getTripId().equals(tripId))) {
            throw new BagItemNotFoundException(ITEM_NOT_FOUND_MESSAGE);
        }

        // 물품과 체크리스트 항목이 같은 행이라 이 삭제로 체크리스트에서도 함께 사라진다
        bagItemRiskLevelRepository.deleteAllByBagItemIdIn(distinctIds);
        bagItemRepository.deleteAll(items);
        return InventoryItemsDeleteResponse.of(items.size());
    }

    private void findTripOwnedBy(Long tripId, Long memberId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        if (!trip.getMemberId().equals(memberId)) {
            throw new TripAccessDeniedException("본인의 여행만 수정할 수 있습니다.");
        }
    }

    private BagItem findItemOwnedBy(Long itemId, Long memberId, String accessDeniedMessage) {
        BagItem item = bagItemRepository.findById(itemId)
                .orElseThrow(() -> new BagItemNotFoundException(ITEM_NOT_FOUND_MESSAGE));
        if (!item.isOwnedBy(memberId)) {
            throw new BagItemAccessDeniedException(accessDeniedMessage);
        }
        return item;
    }
}
