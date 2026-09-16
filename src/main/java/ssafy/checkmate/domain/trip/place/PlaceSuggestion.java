package ssafy.checkmate.domain.trip.place;

import lombok.Getter;

/** 목적지 자동완성 후보 하나. */
@Getter
public class PlaceSuggestion {

    private final String placeId;
    private final String country;
    private final String region;

    public PlaceSuggestion(String placeId, String country, String region) {
        this.placeId = placeId;
        this.country = country;
        this.region = region;
    }
}
