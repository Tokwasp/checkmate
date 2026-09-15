package ssafy.checkmate.domain.weather.geocoding;

import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenMeteoGeocodingClient {

    private final RestClient restClient;

    public OpenMeteoGeocodingClient(@Qualifier("openMeteoGeocodingRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<GeocodingResult> resolve(String destination) {
        try {
            GeocodingSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search")
                            .queryParam("name", destination)
                            .queryParam("count", 1)
                            .queryParam("language", "ko")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(GeocodingSearchResponse.class);

            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                return Optional.empty();
            }
            GeocodingSearchResponse.Result first = response.getResults().get(0);
            return Optional.of(new GeocodingResult(first.getLatitude(), first.getLongitude()));
        } catch (RestClientException e) {
            log.warn("open-meteo geocoding failed for destination={}", destination, e);
            return Optional.empty();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class GeocodingSearchResponse {
        private List<Result> results;

        @Getter
        @Setter
        @NoArgsConstructor
        static class Result {
            private double latitude;
            private double longitude;
        }
    }
}
