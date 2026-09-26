package ssafy.checkmate.domain.checklist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;
import ssafy.checkmate.global.config.BedrockProperties;
import ssafy.checkmate.global.config.QuarantineRerankProperties;

/**
 * 한국 반입 규정 검색·판정(35번 2·3단계) 테스트. 실제 Bedrock 대신 SDK 클라이언트를 모킹한다 —
 * 실호출은 돈이 나가고 CI에 자격증명이 없다.
 */
@ExtendWith(MockitoExtension.class)
class BedrockQuarantineRegulationClientTest {

    private static final ItemAnalysis JERKY =
            new ItemAnalysis("육포", "육류 가공품", List.of("돼지고기", "소고기"), "건조 가공");
    private static final BedrockProperties PROPERTIES =
            new BedrockProperties("ap-northeast-2", "kb-1", "model-x", 12, 4000);

    @Mock
    private BedrockAgentRuntimeClient agentClient;

    @Mock
    private BedrockRuntimeClient runtimeClient;

    private BedrockQuarantineRegulationClient client;

    @Mock
    private QuarantineReranker reranker;

    @BeforeEach
    void setUp() {
        client = new BedrockQuarantineRegulationClient(agentClient, runtimeClient, PROPERTIES,
                new QuarantineReranker(null, new QuarantineRerankProperties(false, "ap-northeast-1", "arn", 50, 5, 0.25)));
    }

    @Test
    @DisplayName("리랭크를 켜면 품목 질의만 후보 풀로 넓게 검색해 리랭크 결과만 싣고, 과태료 질의는 그대로 검색한다")
    void judgeReranksOnlyItemQuery() {
        // given
        BedrockQuarantineRegulationClient rerankingClient =
                new BedrockQuarantineRegulationClient(agentClient, runtimeClient, PROPERTIES, reranker);
        given(reranker.isEnabled()).willReturn(true);
        given(reranker.candidatePool()).willReturn(50);
        givenRetrieved("품목 후보 A", "품목 후보 B");
        given(reranker.rerank(any(), any())).willReturn(List.of("품목 후보 B"));
        givenVerdict("PROHIBITED", "사유", null, "조치");

        // when
        rerankingClient.judge(JERKY);

        // then
        ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(agentClient, times(2)).retrieve(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(request -> request.retrievalConfiguration().vectorSearchConfiguration().numberOfResults())
                .containsExactly(50, 12);
        verify(reranker).rerank("육포 육류 가공품 돼지고기 소고기 건조 가공 반입 검역", List.of("품목 후보 A", "품목 후보 B"));
    }

    @Test
    @DisplayName("리랭크가 실패하면 판정을 멈추지 않고 벡터 상위 number-of-results개로 대신한다")
    void judgeFallsBackToVectorResultsWhenRerankFails() {
        // given
        BedrockQuarantineRegulationClient rerankingClient =
                new BedrockQuarantineRegulationClient(agentClient, runtimeClient, PROPERTIES, reranker);
        given(reranker.isEnabled()).willReturn(true);
        given(reranker.candidatePool()).willReturn(50);
        givenRetrieved("품목 후보 A");
        given(reranker.rerank(any(), any())).willThrow(new IllegalStateException("권한 없음"));
        givenVerdict("PROHIBITED", "사유", null, "조치");

        // when
        rerankingClient.judge(JERKY);

        // then
        assertThat(capturedUserPrompt()).contains("품목 후보 A");
    }

    @Test
    @DisplayName("scope=import 이면서 jurisdictions에 KR인 문서만 검색한다")
    void judgeSearchesOnlyKoreanImportDocuments() {
        // given
        givenRetrieved("규정 A");
        givenVerdict("PROHIBITED", "육류 가공품은 검역 대상이에요.", null, "자진 신고하세요.");

        // when
        client.judge(JERKY);

        // then
        ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(agentClient, times(2)).retrieve(captor.capture());
        RetrieveRequest request = captor.getAllValues().get(0);
        assertThat(request.knowledgeBaseId()).isEqualTo("kb-1");

        RetrievalFilter filter = request.retrievalConfiguration().vectorSearchConfiguration().filter();
        assertThat(filter.andAll()).hasSize(2);
        assertThat(filter.andAll().get(0).equalsValue().key()).isEqualTo("scope");
        assertThat(filter.andAll().get(0).equalsValue().value().asString()).isEqualTo("import");
        assertThat(filter.andAll().get(1).listContains().key()).isEqualTo("jurisdictions");
        assertThat(filter.andAll().get(1).listContains().value().asString()).isEqualTo("KR");
        assertThat(request.retrievalConfiguration().vectorSearchConfiguration().numberOfResults()).isEqualTo(12);
    }

    @Test
    @DisplayName("품목 질의와 과태료·신고 질의 두 개를 각각 검색한다")
    void judgeSearchesItemAndPenaltyQueries() {
        // given
        givenRetrieved("규정 A");
        givenVerdict("PROHIBITED", "사유", null, "조치");

        // when
        client.judge(JERKY);

        // then
        ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(agentClient, times(2)).retrieve(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(request -> request.retrievalQuery().text())
                .containsExactly(
                        "육포 육류 가공품 돼지고기 소고기 건조 가공 반입 검역",
                        "육류 가공품 휴대 반입 과태료 부과기준 자진 신고");
    }

    @Test
    @DisplayName("두 질의에서 같은 청크가 나오면 본문 기준으로 한 번만 프롬프트에 싣는다")
    void judgeDeduplicatesChunks() {
        // given
        givenRetrieved("같은 규정", "같은 규정", "다른 규정");
        givenVerdict("PROHIBITED", "사유", null, "조치");

        // when
        client.judge(JERKY);

        // then
        String prompt = capturedUserPrompt();
        assertThat(prompt.split("같은 규정", -1)).hasSize(2);
        assertThat(prompt).contains("다른 규정");
        assertThat(prompt).contains("품목=육포 | 분류=육류 가공품 | 원재료=돼지고기, 소고기 | 상태=건조 가공");
    }

    @Test
    @DisplayName("도구 사용으로 스키마를 강제하고 temperature는 0으로 보낸다")
    void judgeForcesToolUseWithZeroTemperature() {
        // given
        givenRetrieved("규정 A");
        givenVerdict("PROHIBITED", "사유", null, "조치");

        // when
        client.judge(JERKY);

        // then
        ConverseRequest request = capturedConverseRequest();
        assertThat(request.modelId()).isEqualTo("model-x");
        assertThat(request.inferenceConfig().temperature()).isEqualTo(0.0f);
        assertThat(request.inferenceConfig().maxTokens()).isEqualTo(4000);
        assertThat(request.toolConfig().toolChoice().tool().name()).isEqualTo("report_quarantine_verdict");
        assertThat(request.system().get(0).text()).isEqualTo(BedrockQuarantineRegulationClient.SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("모델이 준 판정·사유·과태료·조치를 해석한다")
    void judge() {
        // given
        givenRetrieved("규정 A");
        givenVerdict("PROHIBITED", "육류 가공품은 축산물 검역 대상이라 휴대 반입이 불가해요.",
                "적발 시 위반 횟수·원산지에 따라 100만~1,000만 원의 과태료가 부과될 수 있어요.",
                "소지하고 있다면 공항 검역대에 자진 신고하세요.");

        // when
        QuarantineVerdict verdict = client.judge(JERKY);

        // then
        assertThat(verdict.level()).isEqualTo(RegulationLevel.PROHIBITED);
        assertThat(verdict.reason()).isEqualTo("육류 가공품은 축산물 검역 대상이라 휴대 반입이 불가해요.");
        assertThat(verdict.penalty()).isEqualTo("적발 시 위반 횟수·원산지에 따라 100만~1,000만 원의 과태료가 부과될 수 있어요.");
        assertThat(verdict.action()).isEqualTo("소지하고 있다면 공항 검역대에 자진 신고하세요.");
    }

    @Test
    @DisplayName("level은 대소문자를 가리지 않고 해석한다")
    void judgeWithLowerCaseLevel() {
        // given
        givenRetrieved("규정 A");
        givenVerdict("restricted", "사유", null, "조치");

        // when
        QuarantineVerdict verdict = client.judge(JERKY);

        // then
        assertThat(verdict.level()).isEqualTo(RegulationLevel.RESTRICTED);
    }

    @Test
    @DisplayName("허용 판정에 과태료·조치가 섞여 와도 버린다")
    void judgeAllowedDropsPenaltyAndAction() {
        // given
        givenRetrieved("규정 A");
        givenVerdict("ALLOWED", "검역 대상이 아닌 일반 공산품이에요.", "과태료가 있어요.", "신고하세요.");

        // when
        QuarantineVerdict verdict = client.judge(new ItemAnalysis("인형", "일반 공산품", List.of(), "완제품"));

        // then
        assertThat(verdict.level()).isEqualTo(RegulationLevel.ALLOWED);
        assertThat(verdict.penalty()).isNull();
        assertThat(verdict.action()).isNull();
    }

    @Test
    @DisplayName("검색 결과가 없어도 판정은 계속 진행한다 — 분류 기준 보수 규칙은 프롬프트가 맡는다")
    void judgeWithoutRetrievedChunks() {
        // given
        givenRetrieved();
        givenVerdict("RESTRICTED", "검역대에서 확인하세요.", null, "검역대에서 확인하세요.");

        // when
        QuarantineVerdict verdict = client.judge(JERKY);

        // then
        assertThat(verdict.level()).isEqualTo(RegulationLevel.RESTRICTED);
    }

    @Test
    @DisplayName("모르는 level이면 추측해서 채우지 않고 예외가 발생한다")
    void judgeWithUnknownLevel() {
        // given
        givenRetrieved("규정 A");
        givenVerdict("MAYBE", "사유", null, null);

        // when // then
        assertThatThrownBy(() -> client.judge(JERKY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("판정 응답의 level을 해석할 수 없습니다.");
    }

    @Test
    @DisplayName("응답에 도구 사용 블록이 없으면 예외가 발생한다")
    void judgeWithoutToolUse() {
        // given
        givenRetrieved("규정 A");
        given(runtimeClient.converse(any(ConverseRequest.class))).willReturn(ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText("텍스트만 왔다"))
                        .build()))
                .stopReason(StopReason.END_TURN)
                .usage(usage())
                .build());

        // when // then
        assertThatThrownBy(() -> client.judge(JERKY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("판정 응답 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("질의문은 원재료·상태가 비어도 공백이 겹치지 않는다")
    void itemQueryWithoutIngredientsAndState() {
        // when
        String query = BedrockQuarantineRegulationClient.itemQuery(
                new ItemAnalysis("인형", "일반 공산품", List.of(), ""));

        // then
        assertThat(query).isEqualTo("인형 일반 공산품 반입 검역");
    }

    private void givenRetrieved(String... texts) {
        List<KnowledgeBaseRetrievalResult> results = java.util.Arrays.stream(texts)
                .map(text -> KnowledgeBaseRetrievalResult.builder()
                        .content(RetrievalResultContent.builder().text(text).build())
                        .build())
                .toList();
        given(agentClient.retrieve(any(RetrieveRequest.class)))
                .willReturn(RetrieveResponse.builder().retrievalResults(results).build());
    }

    private void givenVerdict(String level, String reason, String penalty, String action) {
        var input = Document.mapBuilder()
                .putString("level", level)
                .putString("reason", reason);
        if (penalty != null) {
            input.putString("penalty", penalty);
        }
        if (action != null) {
            input.putString("action", action);
        }
        given(runtimeClient.converse(any(ConverseRequest.class))).willReturn(ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromToolUse(ToolUseBlock.builder()
                                .toolUseId("tool-1")
                                .name("report_quarantine_verdict")
                                .input(input.build())
                                .build()))
                        .build()))
                .stopReason(StopReason.TOOL_USE)
                .usage(usage())
                .build());
    }

    private TokenUsage usage() {
        return TokenUsage.builder().inputTokens(10).outputTokens(5).totalTokens(15).build();
    }

    private ConverseRequest capturedConverseRequest() {
        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(runtimeClient).converse(captor.capture());
        return captor.getValue();
    }

    private String capturedUserPrompt() {
        return capturedConverseRequest().messages().get(0).content().get(0).text();
    }
}
