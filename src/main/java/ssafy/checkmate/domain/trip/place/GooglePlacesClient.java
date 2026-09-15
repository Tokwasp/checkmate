package ssafy.checkmate.domain.trip.place;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ssafy.checkmate.domain.trip.exception.TripInvalidDestinationException;

/**
 * Google Places API(New) 호출. Autocomplete로 후보를 검색하고, 사용자가 고른 하나를
 * Place Details로 확정해 이름과 좌표를 받아온다(docs/travel/docs.md §2-2).
 *
 * 카드 미보유로 실제 호출 검증은 못 했다 — 필드명은 Google 공식 문서 기준
 * (docs.md §9-8, GooglePlacesClientManualIT 참고).
 */
@Component
public class GooglePlacesClient {

    private static final String LANGUAGE_CODE = "ko";
    private static final String DETAILS_FIELD_MASK = "displayName,location";

    private final RestClient restClient;

    public GooglePlacesClient(RestClient googlePlacesRestClient) {
        this.restClient = googlePlacesRestClient;
    }

    /** POST /v1/places:autocomplete — 관련도 높은 순 후보 목록 */
    public List<PlaceSuggestion> autocomplete(String input, String sessionToken) {
        AutocompleteRequest request = new AutocompleteRequest(input, sessionToken, LANGUAGE_CODE);
        AutocompleteResponse response = restClient.post()
                .uri("/v1/places:autocomplete")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AutocompleteResponse.class);

        if (response == null || response.getSuggestions() == null) {
            return List.of();
        }
        return response.getSuggestions().stream()
                .map(Suggestion::toPlaceSuggestion)
                .toList();
    }

    /** GET /v1/places/{placeId} — displayName/location만 요청해 가장 싼 Essentials 등급으로 고정 */
    public PlaceDetails getDetails(String placeId, String sessionToken) {
        PlaceDetailsResponse response = restClient.get()
                .uri("/v1/places/{placeId}?sessionToken={sessionToken}", placeId, sessionToken)
                .header("X-Goog-FieldMask", DETAILS_FIELD_MASK)
                .retrieve()
                .body(PlaceDetailsResponse.class);

        if (response == null || response.getDisplayName() == null || response.getLocation() == null) {
            throw new TripInvalidDestinationException();
        }
        return new PlaceDetails(
                response.getDisplayName().getText(),
                response.getLocation().getLatitude(),
                response.getLocation().getLongitude());
    }

    private static class AutocompleteRequest {
        @Getter
        private final String input;
        @Getter
        private final String sessionToken;
        @Getter
        private final String languageCode;

        private AutocompleteRequest(String input, String sessionToken, String languageCode) {
            this.input = input;
            this.sessionToken = sessionToken;
            this.languageCode = languageCode;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class AutocompleteResponse {
        private List<Suggestion> suggestions;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class Suggestion {
        private PlacePrediction placePrediction;

        private PlaceSuggestion toPlaceSuggestion() {
            return new PlaceSuggestion(placePrediction.getPlaceId(), placePrediction.getText().getText());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class PlacePrediction {
        private String placeId;
        private PlaceText text;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class PlaceText {
        private String text;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class PlaceDetailsResponse {
        private PlaceText displayName;
        private Location location;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class Location {
        private Double latitude;
        private Double longitude;
    }
}
