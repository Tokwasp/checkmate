package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;

@Getter
public class ChecklistItemResponse {

    private final Long itemId;
    private final String name;
    private final String category;
    private final int quantity;
    private final boolean checked;
    private final Long bagId;
    private final String bagName;
    private final String carriageType;
    private final String bagType;

    private ChecklistItemResponse(Long itemId, String name, String category, int quantity, boolean checked,
                                   Long bagId, String bagName, String carriageType, String bagType) {
        this.itemId = itemId;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.checked = checked;
        this.bagId = bagId;
        this.bagName = bagName;
        this.carriageType = carriageType;
        this.bagType = bagType;
    }

    public static ChecklistItemResponse of(BagItem bagItem, Bag bag) {
        BagResponse bagResponse = BagResponse.of(bag);
        return new ChecklistItemResponse(
                bagItem.getId(),
                bagItem.getName(),
                bagItem.getCategory(),
                bagItem.getQuantity(),
                bagItem.isDepartureChecked(),
                bagResponse.getBagId(),
                bagResponse.getName(),
                bagResponse.getCarriageType(),
                bagResponse.getType());
    }
}
