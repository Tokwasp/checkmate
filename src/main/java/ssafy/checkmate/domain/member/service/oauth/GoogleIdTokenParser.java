package ssafy.checkmate.domain.member.service.oauth;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class GoogleIdTokenParser {

    private static final int PAYLOAD_SEGMENT_INDEX = 1;
    private static final int MIN_JWT_SEGMENTS = 2;

    private final ObjectMapper objectMapper;

    public GoogleUserInfo parse(String idToken) {
        JsonNode claims = readClaims(decodePayload(idToken));
        return new GoogleUserInfo(
                claims.path("email").asString(null),
                claims.path("name").asString(null),
                claims.path("picture").asString(null)
        );
    }

    private String decodePayload(String idToken) {
        String[] segments = idToken.split("\\.");
        if (segments.length < MIN_JWT_SEGMENTS) {
            throw new IllegalArgumentException("유효하지 않은 구글 ID 토큰입니다.");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(segments[PAYLOAD_SEGMENT_INDEX]);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 구글 ID 토큰입니다.", e);
        }
    }

    private JsonNode readClaims(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 구글 ID 토큰입니다.", e);
        }
    }
}
