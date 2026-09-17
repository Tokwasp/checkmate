package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.BagItem;

@Getter
public class InventoryItemResponse {

    private final Long itemId;
    private final String name;
    private final int quantity;
    private final String imageUrl;
    private final String riskLevel;

    private InventoryItemResponse(Long itemId, String name, int quantity, String imageUrl, String riskLevel) {
        this.itemId = itemId;
        this.name = name;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
        this.riskLevel = riskLevel;
    }

    public static InventoryItemResponse of(BagItem bagItem, String riskLevel) {
        return new InventoryItemResponse(
                bagItem.getId(), bagItem.getName(), bagItem.getQuantity(), bagItem.getImageUrl(), riskLevel);
    }
}
