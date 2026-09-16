package ssafy.checkmate.domain.checklist.dto.response;

import java.util.List;
import lombok.Getter;

@Getter
public class InventoryBagsResponse {

    private final List<InventoryBagResponse> bags;

    private InventoryBagsResponse(List<InventoryBagResponse> bags) {
        this.bags = bags;
    }

    public static InventoryBagsResponse of(List<InventoryBagResponse> bags) {
        return new InventoryBagsResponse(bags);
    }
}
