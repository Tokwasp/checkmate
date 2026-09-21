package ssafy.checkmate.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GmsProperties.class)
public class GmsClientConfig {

    /** Anthropic Messages API 버전 헤더. GMS가 그대로 전달한다. */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Bean
    public RestClient gmsRestClient(GmsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }

    /** Anthropic 경로용. OpenAI 경로와 달리 {@code Authorization: Bearer}가 아니라 {@code x-api-key}로 인증한다. */
    @Bean
    public RestClient gmsVisionRestClient(GmsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.visionBaseUrl())
                .defaultHeader("x-api-key", properties.apiKey())
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
    }
}
