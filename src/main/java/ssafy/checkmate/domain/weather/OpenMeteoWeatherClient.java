package ssafy.checkmate.domain.weather;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ssafy.checkmate.domain.weather.geocoding.GeocodingResult;
import ssafy.checkmate.domain.weather.geocoding.OpenMeteoGeocodingClient;

@Slf4j
@Component
public class OpenMeteoWeatherClient implements WeatherClient {

    private static final int MAX_FORECAST_DAYS = 16;

    private final OpenMeteoGeocodingClient geocodingClient;
    private final RestClient restClient;

    public OpenMeteoWeatherClient(OpenMeteoGeocodingClient geocodingClient,
                                   @Qualifier("openMeteoForecastRestClient") RestClient restClient) {
        this.geocodingClient = geocodingClient;
        this.restClient = restClient;
    }

    @Override
    public List<DailyWeather> getForecast(String destination, LocalDate startDate, LocalDate endDate) {
        LocalDate maxDate = LocalDate.now().plusDays(MAX_FORECAST_DAYS - 1);
        if (startDate.isAfter(maxDate)) {
            return List.of();
        }
        LocalDate clampedEndDate = endDate.isAfter(maxDate) ? maxDate : endDate;

        return geocodingClient.resolve(destination)
                .map(coordinate -> fetchForecast(coordinate, startDate, clampedEndDate))
                .orElseGet(List::of);
    }

    private List<DailyWeather> fetchForecast(GeocodingResult coordinate, LocalDate startDate, LocalDate endDate) {
        try {
            ForecastResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/forecast")
                            .queryParam("latitude", coordinate.getLatitude())
                            .queryParam("longitude", coordinate.getLongitude())
                            .queryParam("daily", "weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                            .queryParam("timezone", "auto")
                            .queryParam("start_date", startDate)
                            .queryParam("end_date", endDate)
                            .build())
                    .retrieve()
                    .body(ForecastResponse.class);

            if (response == null || response.getDaily() == null) {
                return List.of();
            }
            return toDailyWeathers(response.getDaily());
        } catch (RestClientException e) {
            log.warn("open-meteo forecast failed", e);
            return List.of();
        }
    }

    private static List<DailyWeather> toDailyWeathers(ForecastResponse.Daily daily) {
        List<DailyWeather> weathers = new ArrayList<>();
        List<String> time = daily.getTime();
        for (int i = 0; i < time.size(); i++) {
            weathers.add(new DailyWeather(
                    LocalDate.parse(time.get(i)),
                    toWeatherType(daily.getWeathercode().get(i)),
                    daily.getTemperature2mMin().get(i),
                    daily.getTemperature2mMax().get(i),
                    daily.getPrecipitationProbabilityMax().get(i)));
        }
        return weathers;
    }

    private static WeatherType toWeatherType(int code) {
        if (isSnow(code)) {
            return WeatherType.SNOWY;
        }
        if (isRain(code)) {
            return WeatherType.RAINY;
        }
        if (code == 3 || code == 45 || code == 48) {
            return WeatherType.OVERCAST;
        }
        if (code == 1 || code == 2) {
            return WeatherType.CLOUDY;
        }
        return WeatherType.SUNNY;
    }

    private static boolean isRain(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || (code >= 95 && code <= 99);
    }

    private static boolean isSnow(int code) {
        return (code >= 71 && code <= 77) || code == 85 || code == 86;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class ForecastResponse {
        private Daily daily;

        @Getter
        @Setter
        @NoArgsConstructor
        static class Daily {
            private List<String> time;
            private List<Integer> weathercode;

            @JsonProperty("temperature_2m_max")
            private List<Double> temperature2mMax;

            @JsonProperty("temperature_2m_min")
            private List<Double> temperature2mMin;

            @JsonProperty("precipitation_probability_max")
            private List<Integer> precipitationProbabilityMax;
        }
    }
}
