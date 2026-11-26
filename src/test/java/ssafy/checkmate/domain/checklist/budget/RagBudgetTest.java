package ssafy.checkmate.domain.checklist.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.CountTokensRequest;
import software.amazon.awssdk.services.bedrockruntime.model.CountTokensResponse;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.domain.checklist.service.BedrockAviationRegulationClient;
import ssafy.checkmate.domain.checklist.service.BedrockQuarantineRegulationClient;
import ssafy.checkmate.domain.checklist.service.ItemAnalysis;
import ssafy.checkmate.domain.checklist.service.QuarantineReranker;
import ssafy.checkmate.domain.checklist.service.SecurityCheckTarget;
import ssafy.checkmate.global.config.BedrockProperties;

class RagBudgetTest {

    static final String MODEL = "global.anthropic.claude-haiku-4-5-20251001-v1:0";
    static final String KEY = "checkmate:rag-budget:2026-11-27";
    static final String TEST_KEY = "checkmate:rag-budget:test:2026-11-27";
    static final long LIMIT = 10_000_000_000L;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T15:02:00Z"), ZoneOffset.UTC);

    private final UpstashRagBudgetStore store = mock(UpstashRagBudgetStore.class);
    private final BedrockRuntimeClient runtime = mock(BedrockRuntimeClient.class);
    private final RagBudget budget = new RagBudget(properties(true), store, new RagTokenCounter(runtime), CLOCK);
    private final ConverseRequest request = ConverseRequest.builder()
            .modelId(MODEL)
            .system(system -> system.text("system"))
            .messages(message -> message.role("user").content(block -> block.text("rule")))
            .toolConfig(config -> config.tools(tool -> tool.toolSpec(spec -> spec.name("verdict"))))
            .inferenceConfig(config -> config.maxTokens(4_000))
            .build();

    @Test
    void reservesBeforeBudgetedCallsAndSettlesActualGeneration() {
        given(runtime.countTokens(any(CountTokensRequest.class)))
                .willReturn(CountTokensResponse.builder().inputTokens(11_313).build());
        given(runtime.converse(request)).willReturn(ConverseResponse.builder()
                .usage(usage -> usage.inputTokens(11_313).outputTokens(1_000).totalTokens(12_313))
                .build());

        try (RagBudget.Request reservation = budget.start(MODEL, 2, true, 4_000)) {
            reservation.chargeRetrieve();
            reservation.chargeRerank("술", List.of("규정"));
            reservation.chargeRetrieve();
            reservation.converse(runtime, request);
        }

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CountTokensRequest> counted = ArgumentCaptor.forClass(CountTokensRequest.class);
        var order = inOrder(store, runtime);
        order.verify(store).reserve(eq(KEY), id.capture(), eq(93_000_000L), eq(LIMIT),
                eq(Instant.parse("2026-11-28T15:00:00Z").getEpochSecond()));
        order.verify(runtime).countTokens(counted.capture());
        order.verify(runtime).converse(request);
        order.verify(store).settle(KEY, id.getValue(), 30_469_500L);
        assertThat(counted.getValue().modelId()).isEqualTo(MODEL.substring(7));
        assertThat(counted.getValue().input().converse().system()).isEqualTo(request.system());
        assertThat(counted.getValue().input().converse().messages()).isEqualTo(request.messages());
        assertThat(counted.getValue().input().converse().toolConfig()).isEqualTo(request.toolConfig());
    }

    @Test
    void bothRealEntryPointsRejectBeforeAwsWhenSharedBudgetIsExhausted() {
        given(store.reserve(eq(KEY), anyString(), anyLong(), eq(LIMIT), anyLong())).willReturn(-1L);
        BedrockAgentRuntimeClient agent = mock(BedrockAgentRuntimeClient.class);
        QuarantineReranker reranker = mock(QuarantineReranker.class);
        given(reranker.isEnabled()).willReturn(true);
        BedrockProperties bedrock = new BedrockProperties("ap-northeast-2", "kb", MODEL, 15, 4_000);
        var aviation = new BedrockAviationRegulationClient(agent, runtime, bedrock, budget);
        var quarantine = new BedrockQuarantineRegulationClient(agent, runtime, bedrock, reranker, budget);

        assertThatThrownBy(() -> aviation.judge(CountryJurisdiction.KR,
                List.of(new SecurityCheckTarget(1L, "책", "가방", "기내"))))
                .isInstanceOfSatisfying(RagBudgetException.class, error ->
                        assertThat(error.getErrorCode().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> quarantine.judge(new ItemAnalysis("책", "기타", null, null)))
                .isInstanceOfSatisfying(RagBudgetException.class, error ->
                        assertThat(error.getErrorCode().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(store).reserve(eq(KEY), anyString(), eq(61_500_000L), eq(LIMIT), anyLong());
        verify(store).reserve(eq(KEY), anyString(), eq(93_000_000L), eq(LIMIT), anyLong());
        verify(store, never()).settle(anyString(), anyString(), anyLong());
        verifyNoInteractions(agent, runtime);
    }

    @Test
    void retainsAttemptedGenerationCostOnTimeout() {
        given(runtime.countTokens(any(CountTokensRequest.class)))
                .willReturn(CountTokensResponse.builder().inputTokens(11_313).build());
        given(runtime.converse(request)).willThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> {
            try (RagBudget.Request reservation = budget.start(MODEL, 1, false, 4_000)) {
                reservation.chargeRetrieve();
                reservation.converse(runtime, request);
            }
        }).isInstanceOf(IllegalStateException.class);
        verify(store).settle(eq(KEY), anyString(), eq(48_469_500L));
    }

    @Test
    void rejectsOversizedPromptBeforeGenerationAndChargesOnlyAttemptedSearch() {
        given(runtime.countTokens(any(CountTokensRequest.class)))
                .willReturn(CountTokensResponse.builder().inputTokens(20_001).build());

        assertThatThrownBy(() -> {
            try (RagBudget.Request reservation = budget.start(MODEL, 1, false, 4_000)) {
                reservation.chargeRetrieve();
                reservation.converse(runtime, request);
            }
        }).isInstanceOf(RagBudgetException.class);
        verify(runtime, never()).converse(any(ConverseRequest.class));
        verify(store).settle(eq(KEY), anyString(), eq(1_500_000L));
    }

    @Test
    void stopsBeforeConverseWhenCountingIsUnavailable() {
        given(runtime.countTokens(any(CountTokensRequest.class))).willThrow(new IllegalStateException("unsupported"));

        assertThatThrownBy(() -> {
            try (RagBudget.Request reservation = budget.start(MODEL, 1, false, 4_000)) {
                reservation.chargeRetrieve();
                reservation.converse(runtime, request);
            }
        }).isInstanceOfSatisfying(RagBudgetException.class, error ->
                assertThat(error.getErrorCode().getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(runtime, never()).converse(any(ConverseRequest.class));
        verify(store).settle(eq(KEY), anyString(), eq(1_500_000L));
    }

    @Test
    void accountsForLongRerankCandidatesAndReleasesUnspentGeneration() {
        try (RagBudget.Request reservation = budget.start(MODEL, 2, true, 4_000)) {
            reservation.chargeRetrieve();
            reservation.chargeRerank("술", Collections.nCopies(50, "a".repeat(1_000)));
        }
        // 질의 포함 500 제한은 후보당 3개 청크가 되므로 150청크에 2과금 단위를 반영한다.
        verify(store).settle(eq(KEY), anyString(), eq(7_500_000L));
        verifyNoInteractions(runtime);
    }

    @Test
    void keepsLegacyCallsWhenBudgetIsDisabled() {
        RagBudget disabled = new RagBudget(properties(false), store, new RagTokenCounter(runtime), CLOCK);
        ConverseResponse response = ConverseResponse.builder().build();
        given(runtime.converse(request)).willReturn(response);

        try (RagBudget.Request reservation = disabled.start("any-model", 1, false, 4_000)) {
            assertThat(reservation.converse(runtime, request)).isSameAs(response);
        }
        verifyNoInteractions(store);
        verify(runtime, never()).countTokens(any(CountTokensRequest.class));
    }

    static RagBudgetProperties properties(boolean enabled) {
        return new RagBudgetProperties(enabled, "https://unused.upstash.io", "fake", 10_000, 1_500, 1_000,
                20_000, 10, "us-east-1", enabled, "example-only-test-secret-1234567890");
    }
}
