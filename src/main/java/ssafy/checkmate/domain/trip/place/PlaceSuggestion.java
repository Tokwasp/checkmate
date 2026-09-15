package ssafy.checkmate.domain.trip.place;

import lombok.Getter;

/** 목적지 자동완성 후보 하나. */
@Getter
public class PlaceSuggestion {

    private final String placeId;
    private final String description;

    public PlaceSuggestion(String placeId, String description) {
        this.placeId = placeId;
        this.description = description;
    }
}
