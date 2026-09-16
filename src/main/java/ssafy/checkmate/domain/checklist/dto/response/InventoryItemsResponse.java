package ssafy.checkmate.domain.checklist.dto.response;

import java.util.List;
import lombok.Getter;

@Getter
public class InventoryItemsResponse {

    private final List<InventoryItemResponse> items;
    private final boolean hasNext;

    private InventoryItemsResponse(List<InventoryItemResponse> items, boolean hasNext) {
        this.items = items;
        this.hasNext = hasNext;
    }

    public static InventoryItemsResponse of(List<InventoryItemResponse> items, boolean hasNext) {
        return new InventoryItemsResponse(items, hasNext);
    }
}
