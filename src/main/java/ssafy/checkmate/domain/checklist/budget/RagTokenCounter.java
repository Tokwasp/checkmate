package ssafy.checkmate.domain.checklist.budget;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseTokensRequest;
import software.amazon.awssdk.services.bedrockruntime.model.CountTokensRequest;

@Slf4j
@Component
class RagTokenCounter {

    private final BedrockRuntimeClient client;

    @Autowired
    RagTokenCounter(RagBudgetProperties properties) {
        this(properties.enabled() ? BedrockRuntimeClient.builder()
                .region(Region.of(properties.countTokensRegion()))
                .build() : null);
    }

    RagTokenCounter(BedrockRuntimeClient client) {
        this.client = client;
    }

    int count(ConverseRequest request) {
        // CountTokens는 글로벌 추론 프로파일 대신 같은 모델의 기반 모델 ID를 사용한다.
        String model = request.modelId().substring("global.".length());
        ConverseTokensRequest prompt = ConverseTokensRequest.builder()
                .system(request.system())
                .messages(request.messages())
                .toolConfig(request.toolConfig())
                .build();
        CountTokensRequest countRequest = CountTokensRequest.builder()
                .modelId(model)
                .input(input -> input.converse(prompt))
                .build();
        try {
            Integer tokens = client.countTokens(countRequest).inputTokens();
            if (tokens != null && tokens > 0) {
                return tokens;
            }
        } catch (RuntimeException e) {
            log.warn("[rag-budget] Bedrock 입력 토큰 확인 실패 model={} type={}", model, e.getClass().getSimpleName());
        }
        throw RagBudgetException.unavailable();
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
