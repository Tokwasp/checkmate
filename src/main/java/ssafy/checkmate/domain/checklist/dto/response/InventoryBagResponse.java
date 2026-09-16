package ssafy.checkmate.domain.checklist.dto.response;

import java.util.List;
import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.Bag;

@Getter
public class InventoryBagResponse {

    private final Long bagId;
    private final String name;
    private final String type;
    private final String carriageType;
    private final int count;
    private final List<String> categories;

    private InventoryBagResponse(Long bagId, String name, String type, String carriageType, int count, List<String> categories) {
        this.bagId = bagId;
        this.name = name;
        this.type = type;
        this.carriageType = carriageType;
        this.count = count;
        this.categories = categories;
    }

    public static InventoryBagResponse of(Bag bag, int count, List<String> categories) {
        BagResponse bagResponse = BagResponse.of(bag);
        return new InventoryBagResponse(
                bagResponse.getBagId(), bagResponse.getName(), bagResponse.getType(), bagResponse.getCarriageType(),
                count, categories);
    }

    /** 모든 가방을 합산한 가짜 '전체' 항목. bagId/type/carriageType은 없다(null). */
    public static InventoryBagResponse ofAll(int count, List<String> categories) {
        return new InventoryBagResponse(null, "전체", null, null, count, categories);
    }
}
