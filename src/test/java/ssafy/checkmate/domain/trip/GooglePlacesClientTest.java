package ssafy.checkmate.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ssafy.checkmate.domain.trip.place.GooglePlacesClient;
import ssafy.checkmate.domain.trip.place.PlaceSuggestion;

/**
 * 자동완성 응답을 국가/지역으로 가르는 부분 테스트.
 * 실제 Google 호출은 {@link GooglePlacesClientManualIT}가 맡는다.
 */
class GooglePlacesClientTest {

    private static final String BASE_URL = "https://places.googleapis.com";

    private List<PlaceSuggestion> autocompleteWith(String suggestionsJson) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(Matchers.containsString("/v1/places:autocomplete")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"suggestions\":" + suggestionsJson + "}", MediaType.APPLICATION_JSON));

        return new GooglePlacesClient(builder.build()).autocomplete("도쿄", "session-1");
    }

    @Test
    @DisplayName("국가 텍스트에 상위 행정구역이 붙어 와도 국가 이름만 남긴다")
    void extractsSupportedCountryFromSecondaryText() {
        List<PlaceSuggestion> suggestions = autocompleteWith("""
                [{"placePrediction":{
                   "placeId":"place-1",
                   "types":["locality"],
                   "structuredFormat":{
                     "mainText":{"text":"시부야구"},
                     "secondaryText":{"text":"일본 도쿄도"}}}}]
                """);

        assertThat(suggestions).singleElement()
                .extracting(PlaceSuggestion::getCountry, PlaceSuggestion::getRegion)
                .containsExactly("일본", "시부야구");
    }

    @Test
    @DisplayName("국가 텍스트가 국가 하나뿐이면 그대로 쓴다")
    void keepsCountryOnlySecondaryText() {
        List<PlaceSuggestion> suggestions = autocompleteWith("""
                [{"placePrediction":{
                   "placeId":"place-2",
                   "types":["locality"],
                   "structuredFormat":{
                     "mainText":{"text":"도쿄"},
                     "secondaryText":{"text":"일본"}}}}]
                """);

        assertThat(suggestions).singleElement()
                .extracting(PlaceSuggestion::getCountry, PlaceSuggestion::getRegion)
                .containsExactly("일본", "도쿄");
    }

    @Test
    @DisplayName("국가 자체 후보는 mainText를 국가로 쓰고 지역은 비운다")
    void mapsCountryTypeSuggestion() {
        List<PlaceSuggestion> suggestions = autocompleteWith("""
                [{"placePrediction":{
                   "placeId":"place-3",
                   "types":["country"],
                   "structuredFormat":{"mainText":{"text":"일본"}}}}]
                """);

        assertThat(suggestions).singleElement()
                .extracting(PlaceSuggestion::getCountry, PlaceSuggestion::getRegion)
                .containsExactly("일본", null);
    }

    @Test
    @DisplayName("지원하지 않는 국가는 구글이 준 텍스트를 그대로 남긴다")
    void keepsRawTextForUnsupportedCountry() {
        List<PlaceSuggestion> suggestions = autocompleteWith("""
                [{"placePrediction":{
                   "placeId":"place-4",
                   "types":["locality"],
                   "structuredFormat":{
                     "mainText":{"text":"뮌헨"},
                     "secondaryText":{"text":"독일 바이에른주"}}}}]
                """);

        assertThat(suggestions).singleElement()
                .extracting(PlaceSuggestion::getCountry, PlaceSuggestion::getRegion)
                .containsExactly("독일 바이에른주", "뮌헨");
    }
}
