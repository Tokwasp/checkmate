package ssafy.checkmate.domain.checklist.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Base64;
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
 * SSAFY GMS의 Anthropic 경로로 이미지를 보내 Claude 비전 모델에게 묻는다(35번 1단계).
 *
 * 텍스트 전용 {@link GmsClient}(OpenAI 경로, {@code /chat/completions})와 <b>형식이 다르다</b> —
 * 이쪽은 Anthropic Messages API({@code /v1/messages})이고 {@code system}이 최상위 필드이며
 * 이미지는 {@code image} 블록(base64)으로 싣는다. OpenAI 경로에 Claude 모델을 보내면
 * {@code model_not_found}(404)가 난다.
 *
 * 이미지는 저장소에 올리지 않고 메모리에서 바로 보내고 버린다. 이미지 블록을 텍스트보다 앞에 두는 것이
 * 권장 순서다. {@code temperature} 같은 샘플링 파라미터는 보내지 않는다 — Opus 4.7 이후 모델은 받지 않아
 * 보내면 400이 난다.
 */
@Slf4j
@Component
public class GmsVisionClient {

    /**
     * 응답 JSON은 짧지만, thinking이 기본으로 켜지는 모델(Opus 5 등)로 바꿔도 잘리지 않도록 넉넉히 잡는다 —
     * thinking 토큰도 이 상한에 포함된다.
     */
    private static final int MAX_TOKENS = 2048;

    private final RestClient restClient;
    private final String model;

    public GmsVisionClient(RestClient gmsVisionRestClient, GmsProperties properties) {
        this.restClient = gmsVisionRestClient;
        this.model = properties.visionModel();
    }

    /** 이미지와 질문을 보내고, 응답의 첫 번째 텍스트 블록을 반환한다. */
    public String ask(String systemPrompt, String userText, String mimeType, byte[] image) {
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("gms.vision-model이 설정되지 않았습니다.");
        }
        MessagesRequest request = new MessagesRequest(model, MAX_TOKENS, systemPrompt, List.of(
                new RequestMessage("user", List.of(
                        RequestBlock.image(mimeType, Base64.getEncoder().encodeToString(image)),
                        RequestBlock.text(userText)))));

        // gms.vision-base-url이 엔드포인트 전체 경로(.../v1/messages)까지 포함한다 — .uri()를 붙이지 않는다.
        MessagesResponse response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MessagesResponse.class);

        return textOf(response);
    }

    private String textOf(MessagesResponse response) {
        if (response == null || response.getContent() == null) {
            throw new GmsVisionResponseException("비전 모델 응답이 비어 있습니다.");
        }
        log.info("[quarantine-check] GMS 응답 model={} 입력토큰={} 출력토큰={} stopReason={}",
                response.getModel(),
                response.getUsage() == null ? null : response.getUsage().getInputTokens(),
                response.getUsage() == null ? null : response.getUsage().getOutputTokens(),
                response.getStopReason());

        // 안전 분류기가 거절하면 200으로 오되 내용이 없다 — 빈 응답과 구분해 원인을 남긴다.
        if ("refusal".equals(response.getStopReason())) {
            throw new GmsVisionResponseException("비전 모델이 요청을 거절했습니다.");
        }
        if ("max_tokens".equals(response.getStopReason())) {
            log.warn("[quarantine-check] 비전 응답이 토큰 상한(max_tokens={})에서 잘렸다 — 해석에 실패할 수 있다", MAX_TOKENS);
        }
        return response.getContent().stream()
                .filter(block -> "text".equals(block.getType()) && block.getText() != null)
                .map(ResponseBlock::getText)
                .findFirst()
                .orElseThrow(() -> new GmsVisionResponseException("비전 모델 응답에 텍스트 블록이 없습니다."));
    }

    public static class GmsVisionResponseException extends RuntimeException {
        public GmsVisionResponseException(String message) {
            super(message);
        }
    }

    private static class MessagesRequest {
        @Getter
        private final String model;
        @Getter
        @JsonProperty("max_tokens")
        private final int maxTokens;
        @Getter
        private final String system;
        @Getter
        private final List<RequestMessage> messages;

        private MessagesRequest(String model, int maxTokens, String system, List<RequestMessage> messages) {
            this.model = model;
            this.maxTokens = maxTokens;
            this.system = system;
            this.messages = messages;
        }
    }

    private static class RequestMessage {
        @Getter
        private final String role;
        @Getter
        private final List<RequestBlock> content;

        private RequestMessage(String role, List<RequestBlock> content) {
            this.role = role;
            this.content = content;
        }
    }

    /** {@code image}(base64) 또는 {@code text} 블록. 안 쓰는 필드는 null이라 직렬화에서 빠진다. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private static class RequestBlock {
        @Getter
        private final String type;
        @Getter
        private final String text;
        @Getter
        private final ImageSource source;

        private RequestBlock(String type, String text, ImageSource source) {
            this.type = type;
            this.text = text;
            this.source = source;
        }

        private static RequestBlock text(String text) {
            return new RequestBlock("text", text, null);
        }

        private static RequestBlock image(String mimeType, String base64) {
            return new RequestBlock("image", null, new ImageSource(mimeType, base64));
        }
    }

    private static class ImageSource {
        @Getter
        private final String type = "base64";
        @Getter
        @JsonProperty("media_type")
        private final String mediaType;
        @Getter
        private final String data;

        private ImageSource(String mediaType, String data) {
            this.mediaType = mediaType;
            this.data = data;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class MessagesResponse {
        private String model;
        private List<ResponseBlock> content;
        @JsonProperty("stop_reason")
        private String stopReason;
        private Usage usage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class ResponseBlock {
        private String type;
        private String text;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class Usage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;
        @JsonProperty("output_tokens")
        private Integer outputTokens;
    }
}
