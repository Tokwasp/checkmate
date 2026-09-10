package ssafy.checkmate.domain.member.service.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google")
public record GoogleProperties(String clientId) {
}
