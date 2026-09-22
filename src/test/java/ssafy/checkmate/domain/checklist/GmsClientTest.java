package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ssafy.checkmate.domain.checklist.service.GmsClient;
import ssafy.checkmate.global.config.GmsProperties;

class GmsClientTest {

    private static final String BASE_URL = "https://gms.example.com";

    @Test
    @DisplayName("chat 응답의 첫 번째 메시지 본문을 반환한다")
    void chat() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"[]"}}]}
                        """, MediaType.APPLICATION_JSON));
        GmsClient client = new GmsClient(builder.build(), new GmsProperties(BASE_URL, "key", "test-model", BASE_URL, "test-vision-model"));

        // when
        String content = client.chat("system", "user");

        // then
        assertThat(content).isEqualTo("[]");
    }

    @Test
    @DisplayName("choices가 비어 있으면 예외가 발생한다")
    void chatWithEmptyChoices() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        GmsClient client = new GmsClient(builder.build(), new GmsProperties(BASE_URL, "key", "test-model", BASE_URL, "test-vision-model"));

        // when // then
        assertThatThrownBy(() -> client.chat("system", "user"))
                .isInstanceOf(GmsClient.GmsResponseEmptyException.class);
    }

    /**
     * §8-3·§9-4에서 실측한 "물품이 많으면 응답이 잘려 파싱 실패" 버그의 회귀 테스트 —
     * max_tokens를 명시하면 실제로 요청 본문에 실려 나가는지 확인한다.
     */
    @Test
    @DisplayName("max_tokens를 지정하면 요청 본문에 그대로 실어 보낸다")
    void chatWithMaxTokensSendsItInRequestBody() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL))
                .andExpect(content().string(Matchers.containsString("\"max_tokens\":1840")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"ok"}}]}
                        """, MediaType.APPLICATION_JSON));
        GmsClient client = new GmsClient(builder.build(), new GmsProperties(BASE_URL, "key", "test-model", BASE_URL, "test-vision-model"));

        // when // then — MockRestServiceServer가 본문을 확인하고, 안 맞으면 verify에서 실패한다.
        client.chat("system", "user", 1840);
        server.verify();
    }

    /** 기존 2-인자 호출은 요청 형태가 그대로여야 한다 — max_tokens 필드 자체가 없어야 한다. */
    @Test
    @DisplayName("max_tokens를 안 주면 요청 본문에 그 필드 자체가 없다")
    void chatWithoutMaxTokensOmitsField() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL))
                .andExpect(content().string(Matchers.not(Matchers.containsString("max_tokens"))))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"ok"}}]}
                        """, MediaType.APPLICATION_JSON));
        GmsClient client = new GmsClient(builder.build(), new GmsProperties(BASE_URL, "key", "test-model", BASE_URL, "test-vision-model"));

        // when // then
        client.chat("system", "user");
        server.verify();
    }

    @Test
    @DisplayName("응답이 max_tokens에서 잘리면(finish_reason=length) 경고 로그를 남긴다")
    void chatWarnsWhenResponseIsTruncated() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"{\\"translations\\": {"},"finish_reason":"length"}]}
                        """, MediaType.APPLICATION_JSON));
        GmsClient client = new GmsClient(builder.build(), new GmsProperties(BASE_URL, "key", "test-model", BASE_URL, "test-vision-model"));

        Logger logger = (Logger) LoggerFactory.getLogger(GmsClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        // when
        try {
            client.chat("system", "user", 100);
        } finally {
            logger.detachAppender(appender);
        }

        // then
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("max_tokens=100").contains("잘렸다"));
    }
}
