package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.domain.checklist.service.GmsClient;
import ssafy.checkmate.domain.checklist.service.GmsItemTranslationClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 물품 이름 → 현지어 검색어 번역(30번 검색 앞단). GMS는 모킹하고 요청 문구와 응답 해석만 본다 —
 * 실제 호출은 돈이 나가고 CI에 자격증명이 없다.
 */
class GmsItemTranslationClientTest {

    private final GmsClient gmsClient = mock(GmsClient.class);
    private final GmsItemTranslationClient client =
            new GmsItemTranslationClient(gmsClient, new ObjectMapper());

    @Test
    @DisplayName("이름 목록을 한 번에 보내고 이름 → 현지어 검색어로 받는다")
    void translate() {
        given(gmsClient.chat(anyString(), anyString())).willReturn("""
                {"translations": {"칼": "刃物 ナイフ 凶器", "치약": "歯磨き粉 液体物"}}
                """);

        Map<String, String> terms = client.translate(List.of("칼", "치약"), CountryJurisdiction.JP);

        assertThat(terms).containsExactly(
                Map.entry("칼", "刃物 ナイフ 凶器"),
                Map.entry("치약", "歯磨き粉 液体物"));
        verify(gmsClient, times(1)).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("사용자 프롬프트에 목표 언어 이름과 물품 목록이 들어간다")
    void userPromptCarriesLanguageAndNames() {
        given(gmsClient.chat(anyString(), anyString())).willReturn("""
                {"translations": {"칼": "刀具"}}
                """);

        client.translate(List.of("칼"), CountryJurisdiction.TW);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(gmsClient).chat(anyString(), captor.capture());
        assertThat(captor.getValue()).contains("중국어 번체").contains("- 칼");
    }

    @Test
    @DisplayName("모델이 코드펜스로 감싸도 벗겨서 읽는다")
    void translateStripsCodeFence() {
        given(gmsClient.chat(anyString(), anyString())).willReturn("""
                ```json
                {"translations": {"가위": "はさみ 刃物"}}
                ```
                """);

        assertThat(client.translate(List.of("가위"), CountryJurisdiction.JP))
                .containsEntry("가위", "はさみ 刃物");
    }

    @Test
    @DisplayName("요청하지 않은 이름과 빈 값은 버린다 — 엉뚱한 낱말이 질의를 흐리면 안 된다")
    void translateDropsUnknownAndBlank() {
        given(gmsClient.chat(anyString(), anyString())).willReturn("""
                {"translations": {"칼": "刃物", "없던물품": "謎", "치약": "  "}}
                """);

        assertThat(client.translate(List.of("칼", "치약"), CountryJurisdiction.JP))
                .containsExactly(Map.entry("칼", "刃物"));
    }

    @Test
    @DisplayName("한 번 실패하면 한 번 더 부른다")
    void translateRetriesOnce() {
        given(gmsClient.chat(anyString(), anyString()))
                .willThrow(new IllegalStateException("일시 오류"))
                .willReturn("""
                        {"translations": {"칼": "刃物"}}
                        """);

        assertThat(client.translate(List.of("칼"), CountryJurisdiction.JP))
                .containsEntry("칼", "刃物");
        verify(gmsClient, times(2)).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("재시도까지 실패하면 빈 결과를 준다 — 검사 자체를 멈추지는 않는다")
    void translateFallsBackToEmpty() {
        given(gmsClient.chat(anyString(), anyString())).willThrow(new IllegalStateException("장애"));

        assertThat(client.translate(List.of("칼"), CountryJurisdiction.JP)).isEmpty();
        verify(gmsClient, times(2)).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("옮길 이름이 없으면 GMS를 부르지 않는다")
    void translateSkipsEmptyInput() {
        assertThat(client.translate(List.of(), CountryJurisdiction.JP)).isEmpty();
        verify(gmsClient, times(0)).chat(any(), any());
    }
}
