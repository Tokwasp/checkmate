package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.Bag;

@Getter
public class BagResponse {

    private final Long bagId;
    private final String name;
    private final String type;
    private final String carriageType;

    private BagResponse(Long bagId, String name, String type, String carriageType) {
        this.bagId = bagId;
        this.name = name;
        this.type = type;
        this.carriageType = carriageType;
    }

    public static BagResponse of(Bag bag) {
        return new BagResponse(bag.getId(), bag.getName(), bag.getType().name(), carriageTypeOf(bag));
    }

    /** carriageType이 아직 정해지지 않았으면(null) "NONE" 문자열로 내려준다. */
    public static String carriageTypeOf(Bag bag) {
        return bag.getCarriageType() == null ? "NONE" : bag.getCarriageType().getLabel();
    }
}
