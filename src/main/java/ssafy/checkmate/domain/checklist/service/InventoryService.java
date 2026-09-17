package ssafy.checkmate.domain.checklist.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.request.BagItemUpdateRequest;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.exception.BagItemAccessDeniedException;
import ssafy.checkmate.domain.checklist.exception.BagItemBagMismatchException;
import ssafy.checkmate.domain.checklist.exception.BagItemNotFoundException;
import ssafy.checkmate.domain.checklist.exception.BagItemUpdateEmptyException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagItemRiskLevelRepository;

/**
 * 짐 싸기(물건 인식) 27·28번. 가방에 담긴 물품의 수량·체크 여부를 수정하고 삭제한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private static final String ITEM_NOT_FOUND_MESSAGE = "존재하지 않는 인벤토리 물품입니다.";

    private final BagItemRepository bagItemRepository;
    private final BagItemRiskLevelRepository bagItemRiskLevelRepository;

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

    private BagItem findItemOwnedBy(Long itemId, Long memberId, String accessDeniedMessage) {
        BagItem item = bagItemRepository.findById(itemId)
                .orElseThrow(() -> new BagItemNotFoundException(ITEM_NOT_FOUND_MESSAGE));
        if (!item.isOwnedBy(memberId)) {
            throw new BagItemAccessDeniedException(accessDeniedMessage);
        }
        return item;
    }
}
