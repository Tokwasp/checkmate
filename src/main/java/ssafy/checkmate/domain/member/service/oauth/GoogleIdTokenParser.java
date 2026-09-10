package ssafy.checkmate.domain.member.service.oauth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class GoogleIdTokenParser {

    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final ObjectMapper objectMapper;
    private final GoogleProperties googleProperties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GoogleUserInfo parse(String idToken) {
        JsonNode claims = requestTokenInfo(idToken);
        verifyAudience(claims);
        return new GoogleUserInfo(
                claims.path("email").asString(null),
                claims.path("name").asString(null),
                claims.path("picture").asString(null)
        );
    }

    private JsonNode requestTokenInfo(String idToken) {
        HttpResponse<String> response = send(idToken);
        if (response.statusCode() != 200) {
            throw new IllegalArgumentException("유효하지 않은 구글 ID 토큰입니다.");
        }
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> send(String idToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_INFO_URL + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
                .GET()
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("구글 토큰 검증 요청에 실패했습니다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("구글 토큰 검증이 중단되었습니다.", e);
        }
    }

    private void verifyAudience(JsonNode claims) {
        String audience = claims.path("aud").asString(null);
        if (!googleProperties.clientId().equals(audience)) {
            throw new IllegalArgumentException("우리 앱을 위해 발급된 구글 ID 토큰이 아닙니다.");
        }
    }
}
