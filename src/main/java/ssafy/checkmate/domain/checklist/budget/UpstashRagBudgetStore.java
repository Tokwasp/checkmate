package ssafy.checkmate.domain.checklist.budget;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
class UpstashRagBudgetStore {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RagBudgetProperties properties;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final HttpClient http;
    private final String reserveScript;
    private final String settleScript;

    UpstashRagBudgetStore(RagBudgetProperties properties, ObjectMapper mapper) throws IOException {
        this.properties = properties;
        this.mapper = mapper;
        this.endpoint = properties.enabled() ? validateEndpoint(properties) : null;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.reserveScript = new ClassPathResource("redis/rag-budget-reserve.lua").getContentAsString(StandardCharsets.UTF_8);
        this.settleScript = new ClassPathResource("redis/rag-budget-settle.lua").getContentAsString(StandardCharsets.UTF_8);
    }

    long reserve(String key, String id, long amount, long limit, long expiresAt) {
        return eval(reserveScript, key, id, Long.toString(amount), Long.toString(limit), Long.toString(expiresAt));
    }

    long settle(String key, String id, long charged) {
        return eval(settleScript, key, id, Long.toString(charged));
    }

    private long eval(String script, String key, String... args) {
        List<String> command = new ArrayList<>(List.of("EVAL", script, "1", key));
        command.addAll(List.of(args));

        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + properties.redisToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(command)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode result = mapper.readTree(response.body()).path("result");
                if (result.isIntegralNumber()) {
                    return result.longValue();
                }
            }
            log.warn("[rag-budget] Upstash Redis 처리 실패 status={}", response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            log.warn("[rag-budget] Upstash Redis 호출 실패 {}", e.getClass().getSimpleName());
        }
        throw RagBudgetException.unavailable();
    }

    private static URI validateEndpoint(RagBudgetProperties properties) {
        if (properties.redisUrl() == null || properties.redisUrl().isBlank()
                || properties.redisToken() == null || properties.redisToken().isBlank()) {
            throw new IllegalArgumentException("Upstash REST URL과 쓰기 가능한 REST 토큰이 필요합니다.");
        }
        URI url = URI.create(properties.redisUrl());
        if (!"https".equals(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null
                || url.getQuery() != null || url.getFragment() != null) {
            throw new IllegalArgumentException("Upstash HTTPS REST URL을 확인하세요.");
        }
        return url;
    }
}
