package ssafy.checkmate.domain.checklist.dto.response;

import java.util.List;
import lombok.Getter;

@Getter
public class ChecklistItemsResponse {

    private final List<ChecklistItemResponse> items;

    private ChecklistItemsResponse(List<ChecklistItemResponse> items) {
        this.items = items;
    }

    public static ChecklistItemsResponse of(List<ChecklistItemResponse> items) {
        return new ChecklistItemsResponse(items);
    }
}
