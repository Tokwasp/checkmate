package ssafy.checkmate.domain.trip.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.trip.place.PlaceDetails;

@Getter
public class PlaceDetailResponse {

    private final Double latitude;
    private final Double longitude;

    private PlaceDetailResponse(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static PlaceDetailResponse of(PlaceDetails placeDetails) {
        return new PlaceDetailResponse(placeDetails.getLatitude(), placeDetails.getLongitude());
    }
}
