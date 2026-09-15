package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
        GmsClient client = new GmsClient(builder.build(), new GmsProperties(BASE_URL, "key", "test-model"));

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
        GmsClient client = new GmsClient(builder.build(), new GmsProperties(BASE_URL, "key", "test-model"));

        // when // then
        assertThatThrownBy(() -> client.chat("system", "user"))
                .isInstanceOf(GmsClient.GmsResponseEmptyException.class);
    }
}
