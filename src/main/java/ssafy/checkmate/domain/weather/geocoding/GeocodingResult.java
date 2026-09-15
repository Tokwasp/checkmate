package ssafy.checkmate.domain.weather.geocoding;

import lombok.Getter;

@Getter
public class GeocodingResult {

    private final double latitude;
    private final double longitude;

    public GeocodingResult(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
