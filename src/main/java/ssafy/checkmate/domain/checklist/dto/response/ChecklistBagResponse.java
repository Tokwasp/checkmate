package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.Bag;

@Getter
public class ChecklistBagResponse {

    private final Long bagId;
    private final String name;
    private final String type;

    private ChecklistBagResponse(Long bagId, String name, String type) {
        this.bagId = bagId;
        this.name = name;
        this.type = type;
    }

    public static ChecklistBagResponse of(Bag bag) {
        return new ChecklistBagResponse(bag.getId(), bag.getName(), BagResponse.carriageTypeOf(bag));
    }
}
