package ssafy.checkmate.domain.checklist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GmsItemAnalysisClientTest {

    private static final ScanImage IMAGE = new ScanImage(new byte[] {1, 2, 3}, "image/png");

    @Mock
    private GmsVisionClient gmsVisionClient;

    private GmsItemAnalysisClient client() {
        return new GmsItemAnalysisClient(gmsVisionClient, new ObjectMapper());
    }

    @Test
    @DisplayName("비전 모델이 준 JSON을 품목·분류·원재료·상태로 해석한다")
    void analyze() {
        // given
        given(gmsVisionClient.ask(any(), any(), any(), any())).willReturn("""
                {"recognized": true, "name": "육포", "category": "육류 가공품",
                 "ingredients": ["돼지고기", "소고기"], "state": "건조 가공"}
                """);

        // when
        Optional<ItemAnalysis> analysis = client().analyze(IMAGE);

        // then
        assertThat(analysis).isPresent();
        assertThat(analysis.get().name()).isEqualTo("육포");
        assertThat(analysis.get().category()).isEqualTo("육류 가공품");
        assertThat(analysis.get().ingredients()).containsExactly("돼지고기", "소고기");
        assertThat(analysis.get().state()).isEqualTo("건조 가공");
    }

    @Test
    @DisplayName("이미지의 형식과 바이트, 분석 지시문을 그대로 넘겨 호출한다")
    void analyzePassesImageAndPrompt() {
        // given
        given(gmsVisionClient.ask(any(), any(), any(), any())).willReturn("{\"recognized\": false}");

        // when
        client().analyze(IMAGE);

        // then
        verify(gmsVisionClient).ask(eq(GmsItemAnalysisClient.SYSTEM_PROMPT), any(),
                eq("image/png"), eq(new byte[] {1, 2, 3}));
    }

    @Test
    @DisplayName("모델이 코드펜스로 감싸도 벗겨서 해석한다")
    void analyzeWithCodeFence() {
        // given
        String fence = "```";
        given(gmsVisionClient.ask(any(), any(), any(), any())).willReturn(fence + "json\n"
                + "{\"recognized\": true, \"name\": \"치즈\", \"category\": \"유제품\","
                + " \"ingredients\": [\"우유\"], \"state\": \"숙성\"}\n" + fence);

        // when
        Optional<ItemAnalysis> analysis = client().analyze(IMAGE);

        // then
        assertThat(analysis).isPresent();
        assertThat(analysis.get().name()).isEqualTo("치즈");
    }

    @Test
    @DisplayName("recognized가 false이거나 없으면 빈 값을 돌려준다")
    void analyzeWhenNotRecognized() {
        // given
        given(gmsVisionClient.ask(any(), any(), any(), any()))
                .willReturn("{\"recognized\": false, \"name\": \"\", \"category\": \"\"}")
                .willReturn("{\"name\": \"육포\", \"category\": \"육류 가공품\"}");

        // when // then
        assertThat(client().analyze(IMAGE)).isEmpty();
        assertThat(client().analyze(IMAGE)).isEmpty();
    }

    @Test
    @DisplayName("원재료와 상태가 없으면 빈 목록과 빈 문자열로 둔다")
    void analyzeWithoutIngredientsAndState() {
        // given
        given(gmsVisionClient.ask(any(), any(), any(), any())).willReturn(
                "{\"recognized\": true, \"name\": \"인형\", \"category\": \"일반 공산품\"}");

        // when
        Optional<ItemAnalysis> analysis = client().analyze(IMAGE);

        // then
        assertThat(analysis).isPresent();
        assertThat(analysis.get().ingredients()).isEmpty();
        assertThat(analysis.get().state()).isEmpty();
    }

    @Test
    @DisplayName("물품이 있다고 했는데 품목이나 분류가 비어 있으면 응답 오류로 본다")
    void analyzeWithBlankNameOrCategory() {
        // given
        given(gmsVisionClient.ask(any(), any(), any(), any()))
                .willReturn("{\"recognized\": true, \"name\": \" \", \"category\": \"육류 가공품\"}")
                .willReturn("{\"recognized\": true, \"name\": \"육포\", \"category\": \"\"}");

        // when // then
        assertThatThrownBy(() -> client().analyze(IMAGE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client().analyze(IMAGE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("응답이 비어 있으면 예외가 발생한다")
    void analyzeWithBlankResponse() {
        // given
        given(gmsVisionClient.ask(any(), any(), any(), any())).willReturn("  ");

        // when // then
        assertThatThrownBy(() -> client().analyze(IMAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("비전 모델 응답이 비어 있습니다.");
    }

    @Test
    @DisplayName("JSON이 아닌 응답이면 예외가 발생한다")
    void analyzeWithMalformedResponse() {
        // given
        given(gmsVisionClient.ask(any(), any(), any(), any())).willReturn("이건 육포 같아요.");

        // when // then
        assertThatThrownBy(() -> client().analyze(IMAGE)).isInstanceOf(RuntimeException.class);
    }
}
