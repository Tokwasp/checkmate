package ssafy.checkmate.domain.trip.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.trip.place.PlaceSuggestion;

@Getter
public class PlaceSuggestionResponse {

    private final String placeId;
    private final String description;

    private PlaceSuggestionResponse(String placeId, String description) {
        this.placeId = placeId;
        this.description = description;
    }

    public static PlaceSuggestionResponse of(PlaceSuggestion suggestion) {
        return new PlaceSuggestionResponse(suggestion.getPlaceId(), suggestion.getDescription());
    }
}
