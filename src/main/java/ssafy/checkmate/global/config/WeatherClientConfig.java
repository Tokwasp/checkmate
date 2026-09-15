package ssafy.checkmate.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WeatherClientConfig {

    @Bean
    public RestClient openMeteoForecastRestClient() {
        return RestClient.builder().baseUrl("https://api.open-meteo.com").build();
    }
}
