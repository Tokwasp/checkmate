package ssafy.checkmate.domain.trip.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.trip.place.PlaceDetails;

@Getter
public class PlaceDetailResponse {

    private final String destination;
    private final Double latitude;
    private final Double longitude;

    private PlaceDetailResponse(String destination, Double latitude, Double longitude) {
        this.destination = destination;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static PlaceDetailResponse of(PlaceDetails placeDetails) {
        return new PlaceDetailResponse(placeDetails.getName(), placeDetails.getLatitude(), placeDetails.getLongitude());
    }
}
