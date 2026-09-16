package ssafy.checkmate.domain.trip.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.BagItem;

@Getter
public class TripBagItemResponse {

    private final String imageUrl;
    private final String name;
    private final int quantity;

    private TripBagItemResponse(String imageUrl, String name, int quantity) {
        this.imageUrl = imageUrl;
        this.name = name;
        this.quantity = quantity;
    }

    public static TripBagItemResponse of(BagItem item) {
        return new TripBagItemResponse(item.getImageUrl(), item.getName(), item.getQuantity());
    }
}
