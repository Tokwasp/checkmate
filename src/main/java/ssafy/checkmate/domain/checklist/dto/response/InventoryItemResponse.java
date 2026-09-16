package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.BagItem;

@Getter
public class InventoryItemResponse {

    private final Long itemId;
    private final String name;
    private final int quantity;
    private final String imageUrl;

    private InventoryItemResponse(Long itemId, String name, int quantity, String imageUrl) {
        this.itemId = itemId;
        this.name = name;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
    }

    public static InventoryItemResponse of(BagItem bagItem) {
        return new InventoryItemResponse(bagItem.getId(), bagItem.getName(), bagItem.getQuantity(), bagItem.getImageUrl());
    }
}
