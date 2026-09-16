package ssafy.checkmate.domain.trip.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.trip.place.PlaceSuggestion;

@Getter
public class PlaceSuggestionResponse {

    private final String placeId;
    private final String country;
    private final String region;

    private PlaceSuggestionResponse(String placeId, String country, String region) {
        this.placeId = placeId;
        this.country = country;
        this.region = region;
    }

    public static PlaceSuggestionResponse of(PlaceSuggestion suggestion) {
        return new PlaceSuggestionResponse(suggestion.getPlaceId(), suggestion.getCountry(), suggestion.getRegion());
    }
}
