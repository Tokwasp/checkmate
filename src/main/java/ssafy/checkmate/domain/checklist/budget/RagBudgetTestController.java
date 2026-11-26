package ssafy.checkmate.domain.checklist.budget;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ssafy.checkmate.global.config.BedrockProperties;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@Profile("!prod")
@ConditionalOnProperty(prefix = "rag-budget", name = "test-api-enabled", havingValue = "true")
class RagBudgetTestController {

    private final RagBudget budget;
    private final BedrockProperties bedrockProperties;
    private final byte[] key;

    RagBudgetTestController(RagBudget budget, BedrockProperties bedrockProperties, RagBudgetProperties properties) {
        this.budget = budget;
        this.bedrockProperties = bedrockProperties;
        this.key = properties.testApiKey().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/internal/test-api/rag-budget/{check}")
    ApiResponse<RagBudget.Simulation> simulate(@PathVariable String check,
            @RequestHeader(value = "X-Budget-Test-Key", defaultValue = "") String suppliedKey) {
        if (!MessageDigest.isEqual(key, suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "테스트 API 키가 올바르지 않습니다.");
        }
        boolean quarantine = switch (check) {
            case "quarantine" -> true;
            case "security" -> false;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        return ApiResponse.success(budget.simulate(bedrockProperties.modelId(), quarantine));
    }
}
