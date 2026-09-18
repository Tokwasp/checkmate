package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;

@Getter
public class InventoryItemsDeleteResponse {

    private final int deletedCount;

    private InventoryItemsDeleteResponse(int deletedCount) {
        this.deletedCount = deletedCount;
    }

    public static InventoryItemsDeleteResponse of(int deletedCount) {
        return new InventoryItemsDeleteResponse(deletedCount);
    }
}
