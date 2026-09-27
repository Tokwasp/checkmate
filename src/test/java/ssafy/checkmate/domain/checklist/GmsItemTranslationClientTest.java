package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
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
        given(gmsClient.chatJson(anyString(), anyString(), anyInt())).willReturn("""
                {"칼": "刃物 ナイフ 凶器", "치약": "歯磨き粉 液体物"}
                """);

        Map<String, String> terms = client.translate(List.of("칼", "치약"), CountryJurisdiction.JP);

        assertThat(terms).containsExactly(
                Map.entry("칼", "刃物 ナイフ 凶器"),
                Map.entry("치약", "歯磨き粉 液体物"));
        verify(gmsClient, times(1)).chatJson(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("사용자 프롬프트에 목표 언어 이름과 물품 목록이 들어간다")
    void userPromptCarriesLanguageAndNames() {
        given(gmsClient.chatJson(anyString(), anyString(), anyInt())).willReturn("""
                {"칼": "刀具"}
                """);

        client.translate(List.of("칼"), CountryJurisdiction.TW);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(gmsClient).chatJson(anyString(), captor.capture(), anyInt());
        assertThat(captor.getValue()).contains("중국어 번체").contains("- 칼");
    }

    @Test
    @DisplayName("모델이 코드펜스로 감싸도 벗겨서 읽는다")
    void translateStripsCodeFence() {
        given(gmsClient.chatJson(anyString(), anyString(), anyInt())).willReturn("""
                ```json
                {"가위": "はさみ 刃物"}
                ```
                """);

        assertThat(client.translate(List.of("가위"), CountryJurisdiction.JP))
                .containsEntry("가위", "はさみ 刃物");
    }

    @Test
    @DisplayName("요청하지 않은 이름과 빈 값은 버린다 — 엉뚱한 낱말이 질의를 흐리면 안 된다")
    void translateDropsUnknownAndBlank() {
        given(gmsClient.chatJson(anyString(), anyString(), anyInt())).willReturn("""
                {"칼": "刃物", "없던물품": "謎", "치약": "  "}
                """);

        assertThat(client.translate(List.of("칼", "치약"), CountryJurisdiction.JP))
                .containsExactly(Map.entry("칼", "刃物"));
    }

    @Test
    @DisplayName("한 번 실패하면 한 번 더 부른다")
    void translateRetriesOnce() {
        given(gmsClient.chatJson(anyString(), anyString(), anyInt()))
                .willThrow(new IllegalStateException("일시 오류"))
                .willReturn("""
                        {"칼": "刃物"}
                        """);

        assertThat(client.translate(List.of("칼"), CountryJurisdiction.JP))
                .containsEntry("칼", "刃物");
        verify(gmsClient, times(2)).chatJson(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("재시도까지 실패하면 빈 결과를 준다 — 검사 자체를 멈추지는 않는다")
    void translateFallsBackToEmpty() {
        given(gmsClient.chatJson(anyString(), anyString(), anyInt())).willThrow(new IllegalStateException("장애"));

        assertThat(client.translate(List.of("칼"), CountryJurisdiction.JP)).isEmpty();
        verify(gmsClient, times(2)).chatJson(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("옮길 이름이 없으면 GMS를 부르지 않는다")
    void translateSkipsEmptyInput() {
        assertThat(client.translate(List.of(), CountryJurisdiction.JP)).isEmpty();
        verify(gmsClient, times(0)).chatJson(any(), any(), anyInt());
    }

    /**
     * 재시도까지 실패하면 [gms-call-fail] 마커로 ERROR 로그가 남는지 검증한다 — 이 마커가
     * CloudWatch Logs 지표 필터(Metric Filter)의 매칭 대상이라 문구가 바뀌면 알람이 조용히
     * 깨진다. 로그 포맷 자체를 회귀 테스트로 고정해 둔다.
     */
    @Test
    @DisplayName("재시도까지 실패하면 [gms-call-fail] 마커로 ERROR 로그를 남긴다")
    void translateLogsCallFailMarkerWhenBothAttemptsFail() {
        given(gmsClient.chatJson(anyString(), anyString(), anyInt())).willThrow(new IllegalStateException("장애"));
        Logger logger = (Logger) LoggerFactory.getLogger(GmsItemTranslationClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            client.translate(List.of("칼"), CountryJurisdiction.JP);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("[gms-call-fail]")
                        .contains("country=JP")
                        .contains("itemCount=1"));
    }

    /**
     * §8-3·§9-4에서 실측한 "GMS 응답이 잘려 파싱 실패" 버그의 회귀 테스트 — 물품 수에 비례해
     * max_tokens를 명시적으로 보내는지 검증한다. 안 보내면 게이트웨이 기본 상한에 걸려
     * {@code GmsFailureDiagnosisManualIT}에서 재현한 {@code UnexpectedEndOfInputException}이
     * 다시 날 수 있다.
     */
    @Test
    @DisplayName("물품 수에 비례해 max_tokens를 명시적으로 보낸다 — 응답 잘림 방지")
    void translateSendsMaxTokensScaledByItemCount() {
        given(gmsClient.chatJson(anyString(), anyString(), anyInt())).willReturn("{}");

        List<String> twentyThreeItems = java.util.stream.IntStream.range(0, 23)
                .mapToObj(i -> "물품" + i).toList();
        client.translate(twentyThreeItems, CountryJurisdiction.TH);

        ArgumentCaptor<Integer> maxTokensCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(gmsClient).chatJson(anyString(), anyString(), maxTokensCaptor.capture());
        // 23종 x 80토큰/종 = 1,840 — 게이트웨이 기본 상한(관측상 수백 토큰 수준)보다 확실히 크다.
        assertThat(maxTokensCaptor.getValue()).isEqualTo(1840);
    }

    @Test
    @DisplayName("물품이 적어도 max_tokens 최소값(300) 밑으로는 안 내려간다")
    void translateNeverSendsBelowMinimumMaxTokens() {
        given(gmsClient.chatJson(anyString(), anyString(), anyInt())).willReturn("""
                {"칼": "刃物"}
                """);

        client.translate(List.of("칼"), CountryJurisdiction.JP);

        ArgumentCaptor<Integer> maxTokensCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(gmsClient).chatJson(anyString(), anyString(), maxTokensCaptor.capture());
        assertThat(maxTokensCaptor.getValue()).isEqualTo(300);
    }
}
