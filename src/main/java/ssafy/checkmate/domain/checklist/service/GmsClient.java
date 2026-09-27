package ssafy.checkmate.domain.checklist.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ssafy.checkmate.global.config.GmsProperties;

/**
 * SSAFY GMS 게이트웨이 호출. OpenAI 호환 /chat/completions 엔드포인트로 가정한다
 * (docs.md §7-15-1 — 실제 스펙 확인 전까지의 임시 가정).
 */
@Slf4j
@Component
public class GmsClient {

    private final RestClient restClient;
    private final String model;

    public GmsClient(RestClient gmsRestClient, GmsProperties properties) {
        this.restClient = gmsRestClient;
        this.model = properties.model();
    }

    /**
     * systemPrompt/userPrompt로 채팅을 요청하고, 첫 번째 응답 메시지의 본문(content)을 반환한다.
     * {@code max_tokens}를 안 보낸다 — 게이트웨이 기본값에 맡긴다. 응답이 길어질 수 있는
     * 호출(물품이 많거나 답이 긴 경우)은 {@link #chat(String, String, int)}를 써서 명시적으로
     * 상한을 잡을 것. 안 그러면 게이트웨이 기본 상한에서 JSON이 중간에 잘려 파싱이 실패한다
     * ({@link GmsItemTranslationClient}에서 실제로 재현됨 — tools.jackson의
     * {@code UnexpectedEndOfInputException}).
     */
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, null, null);
    }

    /**
     * {@code maxTokens}로 응답 상한을 명시한다. 모델이 상한에 걸려 끊기면({@code finish_reason=length})
     * 파싱 실패로 이어지기 전에 경고 로그를 남긴다 — 상한이 부족했다는 신호와 실제 파싱 예외를
     * 구분하기 위해서다.
     */
    public String chat(String systemPrompt, String userPrompt, int maxTokens) {
        return chat(systemPrompt, userPrompt, (Integer) maxTokens, null);
    }

    /**
     * 응답을 JSON 객체로 강제한다({@code response_format: json_object}). 프롬프트로 "JSON만 쓰라"고
     * 부탁하는 것과 달리, 모델이 문법에 맞지 않는 토큰을 아예 고르지 못하게 막는다 — 닫는 괄호를
     * 하나 더 붙이는 식의 파싱 실패가 사라진다. 다만 max_tokens에서 잘리는 것까지 막지는 못한다.
     *
     * OpenAI 규칙상 메시지 어딘가에 "JSON"이라는 낱말이 있어야 한다.
     */
    public String chatJson(String systemPrompt, String userPrompt, int maxTokens) {
        return chat(systemPrompt, userPrompt, maxTokens, ResponseFormat.JSON_OBJECT);
    }

    private String chat(String systemPrompt, String userPrompt, Integer maxTokens, ResponseFormat responseFormat) {
        ChatCompletionRequest request = new ChatCompletionRequest(model, maxTokens, responseFormat, List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userPrompt)));

        // gms.base-url이 엔드포인트 전체 경로(.../chat/completions)까지 포함하고 있어
        // 여기서 추가 경로를 덧붙이지 않는다(.uri() 생략 시 baseUrl 그대로 호출됨).
        ChatCompletionResponse response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new GmsResponseEmptyException();
        }
        ChatCompletionResponse.Choice choice = response.getChoices().get(0);
        if ("length".equals(choice.getFinishReason())) {
            log.warn("[gms] 응답이 max_tokens={}에서 잘렸다(finish_reason=length) — 파싱이 실패할 수 있다", maxTokens);
        }
        return choice.getMessage().getContent();
    }

    public static class GmsResponseEmptyException extends RuntimeException {
        public GmsResponseEmptyException() {
            super("GMS 응답에 choices가 없습니다.");
        }
    }

    /**
     * {@code maxTokens}·{@code responseFormat}이 null이면 필드 자체를 생략한다 — 기존 호출부의 요청
     * 형태를 그대로 유지하기 위해서다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class ChatCompletionRequest {
        @Getter
        private final String model;
        @Getter
        @JsonProperty("max_tokens")
        private final Integer maxTokens;
        @Getter
        @JsonProperty("response_format")
        private final ResponseFormat responseFormat;
        @Getter
        private final List<ChatMessage> messages;

        private ChatCompletionRequest(String model, Integer maxTokens, ResponseFormat responseFormat,
                List<ChatMessage> messages) {
            this.model = model;
            this.maxTokens = maxTokens;
            this.responseFormat = responseFormat;
            this.messages = messages;
        }
    }

    private static class ResponseFormat {
        private static final ResponseFormat JSON_OBJECT = new ResponseFormat("json_object");

        @Getter
        private final String type;

        private ResponseFormat(String type) {
            this.type = type;
        }
    }

    private static class ChatMessage {
        @Getter
        private final String role;
        @Getter
        private final String content;

        private ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class ChatCompletionResponse {
        private List<Choice> choices;

        @Getter
        @Setter
        @NoArgsConstructor
        static class Choice {
            private ResponseMessage message;
            @JsonProperty("finish_reason")
            private String finishReason;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        static class ResponseMessage {
            private String role;
            private String content;
        }
    }
}
