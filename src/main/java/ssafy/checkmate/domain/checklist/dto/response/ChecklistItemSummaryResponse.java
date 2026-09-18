package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.BagItem;

@Getter
public class ChecklistItemSummaryResponse {

    private final Long itemId;
    private final String name;
    private final boolean checked;
    private final String category;
    private final int quantity;
    private final Long bagId;

    private ChecklistItemSummaryResponse(Long itemId, String name, boolean checked, String category, int quantity, Long bagId) {
        this.itemId = itemId;
        this.name = name;
        this.checked = checked;
        this.category = category;
        this.quantity = quantity;
        this.bagId = bagId;
    }

    public static ChecklistItemSummaryResponse of(BagItem bagItem) {
        return new ChecklistItemSummaryResponse(
                bagItem.getId(), bagItem.getName(), bagItem.isDepartureChecked(),
                bagItem.getCategory(), bagItem.getQuantity(), bagItem.getBagId());
    }
}
