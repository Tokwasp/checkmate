package ssafy.checkmate.domain.trip.place;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.domain.trip.exception.TripInvalidDestinationException;

/**
 * Google Places API(New) 호출. Autocomplete로 국가/지역이 분리된 후보를 검색하고,
 * 사용자가 고른 하나를 Place Details로 확정해 좌표를 받아온다(docs/travel/docs.md §2-2).
 *
 * 카드 미보유로 실제 호출 검증은 못 했다 — 필드명은 Google 공식 문서 기준
 * (docs.md §9-8, GooglePlacesClientManualIT 참고).
 */
@Component
public class GooglePlacesClient {

    private static final String LANGUAGE_CODE = "ko";
    private static final String DETAILS_FIELD_MASK = "location";
    private static final String COUNTRY_TYPE = "country";

    private final RestClient restClient;

    public GooglePlacesClient(RestClient googlePlacesRestClient) {
        this.restClient = googlePlacesRestClient;
    }

    /**
     * POST /v1/places:autocomplete — 관련도 높은 순 후보 목록.
     * 업체·랜드마크·주소가 섞이지 않도록 도시/도(행정구역)/국가로만 제한한다
     * (includedPrimaryTypes).
     */
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

    /**
     * GET /v1/places/{placeId} — location만 요청해 가장 싼 Essentials 등급으로 고정.
     * 국가/지역 텍스트는 프론트가 자동완성 후보에서 이미 받아 생성/수정 요청에 함께 보내므로,
     * 여기서는 placeId 유효성 확인과 좌표 확보만 담당한다.
     */
    public PlaceDetails getDetails(String placeId, String sessionToken) {
        PlaceDetailsResponse response = restClient.get()
                .uri("/v1/places/{placeId}?sessionToken={sessionToken}", placeId, sessionToken)
                .header("X-Goog-FieldMask", DETAILS_FIELD_MASK)
                .retrieve()
                .body(PlaceDetailsResponse.class);

        if (response == null || response.getLocation() == null) {
            throw new TripInvalidDestinationException();
        }
        return new PlaceDetails(response.getLocation().getLatitude(), response.getLocation().getLongitude());
    }

    private static class AutocompleteRequest {
        @Getter
        private final String input;
        @Getter
        private final String sessionToken;
        @Getter
        private final String languageCode;
        @Getter
        private final List<String> includedPrimaryTypes;

        private AutocompleteRequest(String input, String sessionToken, String languageCode) {
            this.input = input;
            this.sessionToken = sessionToken;
            this.languageCode = languageCode;
            this.includedPrimaryTypes = List.of("locality", "administrative_area_level_1", "country");
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

        /**
         * 국가 후보(예: "일본" 자체를 검색)는 secondaryText가 없어 region을 못 채운다 —
         * types에 country가 있으면 mainText를 country로, 아니면 region으로 취급한다.
         *
         * 도시 후보의 국가 텍스트는 "일본 도쿄도"처럼 상위 행정구역이 섞여 오므로 그대로 두면
         * 나중에 반입 규정 검사가 국가를 못 알아본다 —
         * {@link CountryJurisdiction#resolveName(String)}으로 지원 국가 이름만 뽑아낸다.
         * 지원 국가가 없으면 구글이 준 텍스트를 그대로 남긴다(목적지로는 고를 수 있고,
         * 규정 검사만 그 시점에 거절한다).
         */
        private PlaceSuggestion toPlaceSuggestion() {
            StructuredFormat format = placePrediction.getStructuredFormat();
            String mainText = format.getMainText().getText();
            String secondaryText = format.getSecondaryText() != null ? format.getSecondaryText().getText() : null;

            boolean isCountry = placePrediction.getTypes() != null && placePrediction.getTypes().contains(COUNTRY_TYPE);
            String countryText = isCountry ? mainText : secondaryText;
            String country = CountryJurisdiction.resolveName(countryText).orElse(countryText);
            String region = isCountry ? null : mainText;
            return new PlaceSuggestion(placePrediction.getPlaceId(), country, region);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class PlacePrediction {
        private String placeId;
        private List<String> types;
        private StructuredFormat structuredFormat;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class StructuredFormat {
        private PlaceText mainText;
        private PlaceText secondaryText;
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
