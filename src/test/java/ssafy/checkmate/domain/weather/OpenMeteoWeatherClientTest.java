package ssafy.checkmate.domain.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenMeteoWeatherClientTest {

    private static final String BASE_URL = "https://api.open-meteo.com";

    @Test
    @DisplayName("좌표로 일별 예보를 조회해 WeatherType으로 매핑해 반환한다")
    void getForecast() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalDate day1 = LocalDate.now().plusDays(1);
        LocalDate day2 = LocalDate.now().plusDays(2);
        server.expect(requestTo(Matchers.containsString("/v1/forecast")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"daily":{
                          "time":["%s","%s"],
                          "weathercode":[0,61],
                          "temperature_2m_max":[21.0,18.6],
                          "temperature_2m_min":[12.4,14.1],
                          "precipitation_probability_max":[10,80]
                        }}
                        """.formatted(day1, day2), MediaType.APPLICATION_JSON));
        OpenMeteoWeatherClient client = new OpenMeteoWeatherClient(builder.build());

        // when
        List<DailyWeather> forecast = client.getForecast(34.6937, 135.5023, day1, day2);

        // then
        assertThat(forecast).hasSize(2);
        assertThat(forecast.get(0).getDate()).isEqualTo(day1);
        assertThat(forecast.get(0).getWeatherType()).isEqualTo(WeatherType.SUNNY);
        assertThat(forecast.get(0).getMaxTemperature()).isEqualTo(21.0);
        assertThat(forecast.get(0).getMinTemperature()).isEqualTo(12.4);
        assertThat(forecast.get(0).getPrecipitationProbability()).isEqualTo(10);
        assertThat(forecast.get(1).getWeatherType()).isEqualTo(WeatherType.RAINY);
    }

    @Test
    @DisplayName("여행 시작일이 예보 가능 범위(16일)를 벗어나면 API를 호출하지 않고 빈 리스트를 반환한다")
    void getForecastWhenOutOfForecastRange() {
        // given
        RestClient restClient = RestClient.builder().baseUrl(BASE_URL).build();
        OpenMeteoWeatherClient client = new OpenMeteoWeatherClient(restClient);
        LocalDate farFuture = LocalDate.now().plusDays(30);

        // when
        List<DailyWeather> forecast = client.getForecast(34.6937, 135.5023, farFuture, farFuture.plusDays(2));

        // then
        assertThat(forecast).isEmpty();
    }

    @Test
    @DisplayName("여행 시작일이 과거여도 오늘부터의 미래 날짜만 조회한다")
    void getForecastClampsPastStartDateToToday() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalDate today = LocalDate.now();
        server.expect(requestTo(Matchers.allOf(
                        Matchers.containsString("/v1/forecast"),
                        Matchers.containsString("start_date=" + today))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"daily":{
                          "time":["%s"],
                          "weathercode":[0],
                          "temperature_2m_max":[21.0],
                          "temperature_2m_min":[12.4],
                          "precipitation_probability_max":[10]
                        }}
                        """.formatted(today), MediaType.APPLICATION_JSON));
        OpenMeteoWeatherClient client = new OpenMeteoWeatherClient(builder.build());

        // when
        List<DailyWeather> forecast = client.getForecast(34.6937, 135.5023, today.minusDays(5), today.plusDays(2));

        // then
        assertThat(forecast).hasSize(1);
        assertThat(forecast.get(0).getDate()).isEqualTo(today);
    }

    @Test
    @DisplayName("예보 API 호출이 실패하면 빈 리스트를 반환한다")
    void getForecastWhenServerError() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(Matchers.containsString("/v1/forecast")))
                .andRespond(withServerError());
        OpenMeteoWeatherClient client = new OpenMeteoWeatherClient(builder.build());

        // when
        List<DailyWeather> forecast = client.getForecast(34.6937, 135.5023,
                LocalDate.now(), LocalDate.now().plusDays(2));

        // then
        assertThat(forecast).isEmpty();
    }
}
