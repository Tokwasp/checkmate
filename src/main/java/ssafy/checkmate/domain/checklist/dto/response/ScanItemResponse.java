package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.BagItem;

@Getter
public class ScanItemResponse {

    private final Long itemId;
    private final String name;

    private ScanItemResponse(Long itemId, String name) {
        this.itemId = itemId;
        this.name = name;
    }

    public static ScanItemResponse of(BagItem bagItem) {
        return new ScanItemResponse(bagItem.getId(), bagItem.getName());
    }
}
