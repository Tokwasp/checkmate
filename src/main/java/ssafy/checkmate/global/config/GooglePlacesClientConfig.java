package ssafy.checkmate.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GooglePlacesProperties.class)
public class GooglePlacesClientConfig {

    @Bean
    public RestClient googlePlacesRestClient(GooglePlacesProperties properties) {
        return RestClient.builder()
                .baseUrl("https://places.googleapis.com")
                .defaultHeader("X-Goog-Api-Key", properties.apiKey())
                .build();
    }
}
