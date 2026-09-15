package ssafy.checkmate.domain.checklist.service;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ssafy.checkmate.global.config.GmsProperties;

/**
 * SSAFY GMS 게이트웨이 호출. OpenAI 호환 /chat/completions 엔드포인트로 가정한다
 * (docs.md §7-15-1 — 실제 스펙 확인 전까지의 임시 가정).
 */
@Component
public class GmsClient {

    private final RestClient restClient;
    private final String model;

    public GmsClient(RestClient gmsRestClient, GmsProperties properties) {
        this.restClient = gmsRestClient;
        this.model = properties.model();
    }

    /** systemPrompt/userPrompt로 채팅을 요청하고, 첫 번째 응답 메시지의 본문(content)을 반환한다. */
    public String chat(String systemPrompt, String userPrompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(model, List.of(
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
        return response.getChoices().get(0).getMessage().getContent();
    }

    public static class GmsResponseEmptyException extends RuntimeException {
        public GmsResponseEmptyException() {
            super("GMS 응답에 choices가 없습니다.");
        }
    }

    private static class ChatCompletionRequest {
        @Getter
        private final String model;
        @Getter
        private final List<ChatMessage> messages;

        private ChatCompletionRequest(String model, List<ChatMessage> messages) {
            this.model = model;
            this.messages = messages;
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
