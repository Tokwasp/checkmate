package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ssafy.checkmate.domain.checklist.service.GmsVisionClient;
import ssafy.checkmate.global.config.GmsProperties;

/** GMS의 Anthropic 경로(Messages API) 호출 — 요청 형식과 응답 해석을 검증한다. */
class GmsVisionClientTest {

    private static final String URL = "https://gms.example.com/api.anthropic.com/v1/messages";

    @Test
    @DisplayName("Anthropic Messages 형식으로 이미지(base64)를 텍스트보다 앞에 싣고, 첫 텍스트 블록을 반환한다")
    void ask() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(URL)
                .defaultHeader("x-api-key", "key")
                .defaultHeader("anthropic-version", "2023-06-01");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("claude-opus-4-8"))
                .andExpect(jsonPath("$.max_tokens").value(2048))
                .andExpect(jsonPath("$.system").value("시스템 지시"))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("image"))
                .andExpect(jsonPath("$.messages[0].content[0].source.type").value("base64"))
                .andExpect(jsonPath("$.messages[0].content[0].source.media_type").value("image/jpeg"))
                .andExpect(jsonPath("$.messages[0].content[0].source.data").value("AQID"))
                .andExpect(jsonPath("$.messages[0].content[1].type").value("text"))
                .andExpect(jsonPath("$.messages[0].content[1].text").value("분석하세요"))
                .andRespond(withSuccess("""
                        {"model":"claude-opus-4-8","content":[{"type":"text","text":"{\\"ok\\":true}"}],
                         "stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5}}
                        """, MediaType.APPLICATION_JSON));

        // when
        String content = client(builder, "claude-opus-4-8").ask("시스템 지시", "분석하세요", "image/jpeg", new byte[] {1, 2, 3});

        // then
        assertThat(content).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    @DisplayName("thinking 블록이 앞에 와도 첫 번째 텍스트 블록을 골라 반환한다")
    void askSkipsThinkingBlock() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"content":[{"type":"thinking","thinking":""},{"type":"text","text":"답"}],"stop_reason":"end_turn"}
                """, MediaType.APPLICATION_JSON));

        // when
        String content = client(builder, "m").ask("s", "u", "image/png", new byte[] {1});

        // then
        assertThat(content).isEqualTo("답");
    }

    @Test
    @DisplayName("비전 모델이 설정되지 않았으면 호출하지 않고 예외가 발생한다")
    void askWithoutModel() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // when // then
        assertThatThrownBy(() -> client(builder, "").ask("s", "u", "image/png", new byte[] {1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gms.vision-model이 설정되지 않았습니다.");
        server.verify();
    }

    @Test
    @DisplayName("안전 분류기가 거절하면(stop_reason=refusal) 빈 응답과 구분되는 예외가 발생한다")
    void askWhenRefused() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"content":[],"stop_reason":"refusal"}
                """, MediaType.APPLICATION_JSON));

        // when // then
        assertThatThrownBy(() -> client(builder, "m").ask("s", "u", "image/png", new byte[] {1}))
                .isInstanceOf(GmsVisionClient.GmsVisionResponseException.class)
                .hasMessage("비전 모델이 요청을 거절했습니다.");
    }

    @Test
    @DisplayName("텍스트 블록이 하나도 없으면 예외가 발생한다")
    void askWithoutTextBlock() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"content":[{"type":"thinking","thinking":""}],"stop_reason":"end_turn"}
                """, MediaType.APPLICATION_JSON));

        // when // then
        assertThatThrownBy(() -> client(builder, "m").ask("s", "u", "image/png", new byte[] {1}))
                .isInstanceOf(GmsVisionClient.GmsVisionResponseException.class)
                .hasMessage("비전 모델 응답에 텍스트 블록이 없습니다.");
    }

    private GmsVisionClient client(RestClient.Builder builder, String visionModel) {
        return new GmsVisionClient(builder.build(),
                new GmsProperties("https://openai.example.com", "key", "gpt-4o-mini", URL, visionModel));
    }
}
