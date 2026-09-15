package ssafy.checkmate.domain.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ssafy.checkmate.domain.weather.geocoding.GeocodingResult;
import ssafy.checkmate.domain.weather.geocoding.OpenMeteoGeocodingClient;

class OpenMeteoGeocodingClientTest {

    private static final String BASE_URL = "https://geocoding-api.open-meteo.com";

    @Test
    @DisplayName("목적지 이름으로 조회하면 첫 번째 검색 결과의 위경도를 반환한다")
    void resolve() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/search")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"results":[{"latitude":34.6937,"longitude":135.5023}]}
                        """, MediaType.APPLICATION_JSON));
        OpenMeteoGeocodingClient client = new OpenMeteoGeocodingClient(builder.build());

        // when
        Optional<GeocodingResult> result = client.resolve("Osaka");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getLatitude()).isEqualTo(34.6937);
        assertThat(result.get().getLongitude()).isEqualTo(135.5023);
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 Optional을 반환한다")
    void resolveWithNoResults() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/search")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        OpenMeteoGeocodingClient client = new OpenMeteoGeocodingClient(builder.build());

        // when
        Optional<GeocodingResult> result = client.resolve("존재하지않는도시이름");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("지오코딩 API 호출이 실패하면 빈 Optional을 반환한다")
    void resolveWithServerError() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/search")))
                .andRespond(withServerError());
        OpenMeteoGeocodingClient client = new OpenMeteoGeocodingClient(builder.build());

        // when
        Optional<GeocodingResult> result = client.resolve("Osaka");

        // then
        assertThat(result).isEmpty();
    }
}
