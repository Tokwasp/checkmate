package ssafy.checkmate.domain.trip.dto.response;

import java.util.List;
import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.Bag;

@Getter
public class TripBagResponse {

    private final String bagName;
    private final List<TripBagItemResponse> items;

    private TripBagResponse(String bagName, List<TripBagItemResponse> items) {
        this.bagName = bagName;
        this.items = items;
    }

    public static TripBagResponse of(Bag bag, List<TripBagItemResponse> items) {
        return new TripBagResponse(bag.getName(), items);
    }
}
