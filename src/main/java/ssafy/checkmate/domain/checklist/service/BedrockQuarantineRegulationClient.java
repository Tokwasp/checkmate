package ssafy.checkmate.domain.checklist.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.FilterAttribute;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;
import ssafy.checkmate.global.config.BedrockProperties;

/**
 * Bedrock Knowledge Base(S3 Vectors) 기반 구현 — 한국 세관·검역 규정(35번 2·3단계).
 *
 * 항공 보안 규정을 묻는 {@link BedrockAviationRegulationClient}와 같은 지식베이스·같은
 * {@link BedrockProperties}를 쓰되 <b>필터가 다르다</b>: {@code scope=import} 이면서
 * {@code jurisdictions}에 {@code KR}. 이 묶음은 전부 한국어 한국 문서라 30번처럼 물품 이름을
 * 현지어로 옮겨 붙이지 않고 {@code GLOBAL}도 함께 넣지 않는다.
 *
 * 검색(Retrieve)과 생성(Converse)을 나눈 이유는 30번과 같다 — 도구 사용(tool use)으로 출력
 * 스키마를 강제해 텍스트 파싱 실패를 없앤다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BedrockQuarantineRegulationClient implements QuarantineRegulationClient {

    private static final String SCOPE_ATTRIBUTE = "scope";
    private static final String SCOPE_VALUE = "import";
    private static final String JURISDICTIONS_ATTRIBUTE = "jurisdictions";
    private static final String JURISDICTION_KOREA = "KR";
    private static final String TOOL_NAME = "report_quarantine_verdict";
    /** 같은 물품을 두 번 판정했는데 결과가 바뀌면 안전 기능으로서 신뢰를 잃는다. */
    private static final float DETERMINISTIC_TEMPERATURE = 0.0f;

    /** 같은 패키지의 테스트가 실제로 나가는 문구를 그대로 확인한다. */
    static final String SYSTEM_PROMPT = """
            당신은 해외에서 산 물건을 한국으로 가져올 때의 세관·검역 규정을 안내하는 전문가입니다.
            주어진 규정 발췌문만 근거로 삼아, 물품 하나가 한국에 반입 가능한지 판정하세요.

            판정 기준 (level)
            - PROHIBITED: 검역·통관상 가져올 수 없는 물품. 예) 검역증명서 없는 육류 가공품, 생과일, 멸종위기종 제품.
            - RESTRICTED: 수량·한도·신고 조건 안에서만 가져올 수 있는 물품. 예) 면세 한도가 있는 술·담배, 수량 제한이 있는 의약품.
            - ALLOWED: 제한 없이 가져올 수 있는 물품.

            반드시 지킬 것
            - 원재료 중 하나라도 검역 대상이면 가장 엄격한 쪽으로 판정하세요.
            - 이 판정은 여행자가 직접 들고 오는 휴대품만 다룹니다. 검역증명서·식물검역증은 수출국 정부가
              발급하는 통관 서류이므로 휴대품에는 없는 것으로 보세요. "검역증명서(식물검역증)가 있으면
              가능하다"를 이유로 PROHIBITED를 RESTRICTED로 낮추지 마세요.
              단, 식물검역증이 필요한 것은 종자·묘목·구근 등 심는 용도의 식물뿐입니다. 절화(꽃다발) 등
              그 밖의 식물류는 신고 후 검역을 받으면 되므로, 수입금지 대상이 아니면 RESTRICTED로 판정하세요.
            - 의약품·건강기능식품의 "요건확인대상"은 반입 금지가 아니라 별도 확인(의사 소견서 등)을 거치면
              들여올 수 있다는 뜻입니다. PROHIBITED가 아니라 RESTRICTED로 판정하고 필요한 절차를 action에
              안내하세요.
            - 생과실·신선 채소 규정은 생것에만 적용하세요. 완전히 건조했거나 가열·당절임 등으로 가공한
              과일·채소 제품(말린 과일, 잼 등)에는 적용하지 마세요.
            - 사진으로는 원산지·수량을 알 수 없습니다. 모르는 조건을 가정하지 말고, 여행자가 스스로
              맞출 수 있는 조건(수량·용량·금액 한도 등)에 따라 결과가 갈리면 RESTRICTED로 두고 조건을
              안내하세요. 예) 술 2L 한도, 의약품 수량 한도.
            - 발췌문에 "검역증명서를 첨부하지 않은 ...의 경우 과태료"처럼 검역증명서가 없는 물품에
              과태료를 부과한다는 표·조문이 있으면, 그것이 곧 "휴대품으로는 반입할 수 없다"는 근거입니다
              (휴대품에는 검역증명서가 없으므로). 이때는 PROHIBITED로 판정하고 그 표의 금액을 penalty에
              범위로 적으세요. "검역대에서 확인하면 된다"로 끝내지 마세요.
              단, 이 규칙은 과태료 표가 다루는 대상에 이 물품이 실제로 해당할 때만 쓰세요. 예를 들어
              "동물의 생산물(축산물)" 과태료 표는 식물성 원료만으로 만든 가공식품이나 김·미역 같은 해조류에
              적용하지 마세요.
            - 규정 발췌문에 근거가 없을 때는 분류로 가르세요. 여기서 "근거가 없다"는 것은 그 물품·분류에
              관한 규정이 발췌문에 하나도 없는 경우를 말합니다. 이 물품이 해당하지 않는 과태료 표나 일반
              검역 절차만 있다면 근거가 없는 것으로 보고 아래 분류 규칙을 따르세요.
              · 육류 가공품·유제품·수산물·신선 과일·채소·식물·종자·흙·야생동식물 제품이면 ALLOWED가 아니라
                RESTRICTED로 두고 "검역대에서 확인하세요"라고 안내하세요.
              · 그 밖의 분류(일반 공산품 등)는 ALLOWED로 두세요.
            - 판정과 reason은 반드시 일치해야 합니다. reason에 "반입이 불가"라고 쓸 거라면 PROHIBITED입니다.

            필드 작성 규칙 (한국어 "~해요" 말투)
            - reason: 왜 걸렸는지. 분류 → 어떤 검역 대상인지 → 결과 순서의 1~2문장.
              규정 원문을 그대로 옮기지 말고 조문 번호·출처 표기는 넣지 마세요.
              예) "육류 가공품은 축산물 검역 대상이라 휴대 반입이 불가해요."
              ALLOWED면 왜 문제가 없는지 한 문장.
            - penalty: 과태료 안내. 규정 발췌문에 과태료 금액이 있을 때만 쓰고, 없으면 생략하세요.
              금액을 절대 지어내지 마세요. 위반 횟수·원산지에 따라 금액이 갈리면 발췌문에 있는 금액 중
              이 물품에 해당하는 최소~최대를 범위로 적으세요.
              원산지처럼 알 수 없는 조건 때문에 어느 줄에 해당하는지 정할 수 없을 때는, 해당될 가능성이
              있는 줄을 빼지 말고 전부 포함해 범위를 잡으세요. 낮은 줄만 골라 적으면 실제보다 가볍게
              안내하는 셈이 됩니다.
              예) 원재료에 돼지고기가 있으면 아프리카돼지열병(ASF) 발생국산일 수 있으므로 그 줄
                  (최대 1,000만 원)까지 포함해 "100만~1,000만 원"으로 적습니다. 소고기만이면
                  "100만~500만 원"입니다.
              예) "적발 시 위반 횟수·원산지에 따라 100만~1,000만 원의 과태료가 부과될 수 있어요."
              ALLOWED면 생략하세요.
            - action: 어떻게 하면 되는지. PROHIBITED·RESTRICTED면 반드시 쓰고, ALLOWED면 생략하세요.
              예) "소지하고 있다면 공항 검역대에 자진 신고하세요."
              규정 발췌문에 "자진 신고하면 과태료가 부과되지 않고 압수·폐기된다"는 근거가 있을 때만
              "자진 신고하면 과태료 없이 압수·폐기로 끝나요."를 덧붙이세요. 근거가 없으면 덧붙이지 마세요.
              여행자가 준비할 수 없는 서류(검역증명서 등)를 조건으로 "가능하다"고 안내하지 마세요.
            """;

    private final BedrockAgentRuntimeClient bedrockAgentRuntimeClient;
    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final BedrockProperties properties;
    private final QuarantineReranker reranker;

    @Override
    public QuarantineVerdict judge(ItemAnalysis item) {
        String regulations = retrieveRegulations(item);
        return generateVerdict(regulations, item);
    }

    /**
     * 질의 둘을 각각 검색해 청크를 모으고 본문 기준으로 중복을 제거한다.
     *
     * ① 이 물품이 어떤 규정에 걸리는지 — 품목·분류·원재료·상태.
     * ② 걸렸을 때의 과태료·신고 방법 — 과태료 금액은 <b>부과기준 문서</b>(가축전염병 예방법 시행령
     * 별표 3)에 있는데, 품목 질의만으로는 그 문서가 상위에 오르지 않을 수 있다. 그러면 판정문에서
     * 과태료가 빠진다.
     *
     * 품목 질의만 넓게 받아 리랭크한다({@link QuarantineReranker}). 과태료 질의는 리랭크하지 않는다 —
     * 육류·유제품의 금지 근거인 "검역증명서 미첨부 과태료" 청크가 벡터 12~13위에 있어 좁히면 빠진다.
     */
    String retrieveRegulations(ItemAnalysis item) {
        RetrievalFilter filter = importFilter();

        long startedAt = System.nanoTime();
        Set<String> chunks = new LinkedHashSet<>(itemChunks(itemQuery(item), filter));
        chunks.addAll(retrieveFor(penaltyQuery(item), filter, properties.numberOfResults()));

        log.info("[quarantine-check] 규정 검색 완료 청크={}건(중복 제거 후) 리랭크={} 소요={}ms",
                chunks.size(), reranker.isEnabled(), elapsedMillisSince(startedAt));
        return String.join("\n\n---\n\n", chunks);
    }

    /** 리랭크가 실패하면 판정을 멈추지 않고 기존 방식(벡터 상위 number-of-results개)으로 돌아간다. */
    private List<String> itemChunks(String query, RetrievalFilter filter) {
        if (!reranker.isEnabled()) {
            return retrieveFor(query, filter, properties.numberOfResults());
        }
        List<String> candidates = retrieveFor(query, filter, reranker.candidatePool());
        try {
            List<String> reranked = reranker.rerank(query, candidates);
            log.info("[quarantine-check] 품목 질의 리랭크 후보={}건 채택={}건", candidates.size(), reranked.size());
            return reranked;
        } catch (RuntimeException e) {
            log.warn("[quarantine-check] 리랭크 실패 — 벡터 상위 {}건으로 대신한다 원인={}",
                    properties.numberOfResults(), e.getMessage());
            return candidates.stream().limit(properties.numberOfResults()).toList();
        }
    }

    static String itemQuery(ItemAnalysis item) {
        return "%s %s %s %s 반입 검역".formatted(
                item.name(), item.category(), String.join(" ", item.ingredients()), item.state()).replaceAll("\\s+", " ");
    }

    static String penaltyQuery(ItemAnalysis item) {
        return "%s 휴대 반입 과태료 부과기준 자진 신고".formatted(item.category());
    }

    private List<String> retrieveFor(String query, RetrievalFilter filter, int numberOfResults) {
        RetrieveResponse response = bedrockAgentRuntimeClient.retrieve(RetrieveRequest.builder()
                .knowledgeBaseId(properties.knowledgeBaseId())
                .retrievalQuery(KnowledgeBaseQuery.builder().text(query).build())
                .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                        .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                .numberOfResults(numberOfResults)
                                .filter(filter)
                                .build())
                        .build())
                .build());

        List<String> texts = response.retrievalResults().stream()
                .map(result -> result.content().text())
                .toList();
        if (texts.isEmpty()) {
            log.warn("[quarantine-check] 규정 검색 결과가 없다 — 분류 기준 보수 규칙으로 판정된다 질의={}", query);
        }
        return texts;
    }

    /** scope=import 이면서 한국(KR) 문서만 본다. */
    private RetrievalFilter importFilter() {
        return RetrievalFilter.fromAndAll(List.of(
                RetrievalFilter.fromEqualsValue(attribute(SCOPE_ATTRIBUTE, SCOPE_VALUE)),
                RetrievalFilter.fromListContains(attribute(JURISDICTIONS_ATTRIBUTE, JURISDICTION_KOREA))));
    }

    private FilterAttribute attribute(String key, String value) {
        return FilterAttribute.builder().key(key).value(Document.fromString(value)).build();
    }

    /** 도구 사용으로 출력 스키마를 강제해 판정을 받는다. */
    private QuarantineVerdict generateVerdict(String regulations, ItemAnalysis item) {
        long startedAt = System.nanoTime();
        ConverseResponse response = bedrockRuntimeClient.converse(ConverseRequest.builder()
                .modelId(properties.modelId())
                .system(SystemContentBlock.fromText(SYSTEM_PROMPT))
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(userPrompt(regulations, item)))
                        .build())
                .toolConfig(verdictToolConfig())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(properties.maxTokens())
                        .temperature(DETERMINISTIC_TEMPERATURE)
                        .build())
                .build());

        log.info("[quarantine-check] 판정 생성 완료 model={} 입력토큰={} 출력토큰={} stopReason={} 소요={}ms",
                properties.modelId(), response.usage().inputTokens(), response.usage().outputTokens(),
                response.stopReasonAsString(), elapsedMillisSince(startedAt));
        if (response.stopReason() == StopReason.MAX_TOKENS) {
            log.warn("[quarantine-check] 응답이 토큰 상한에서 잘렸다 — 판정 문구가 잘렸을 수 있다");
        }
        return parseVerdict(response);
    }

    private static long elapsedMillisSince(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    String userPrompt(String regulations, ItemAnalysis item) {
        return """
                [규정 발췌문]
                %s

                [물품]
                - 품목=%s | 분류=%s | 원재료=%s | 상태=%s
                """.formatted(regulations, item.name(), item.category(),
                item.ingredients().isEmpty() ? "알 수 없음" : String.join(", ", item.ingredients()),
                item.state().isBlank() ? "알 수 없음" : item.state());
    }

    private ToolConfiguration verdictToolConfig() {
        Document schema = Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", Document.mapBuilder()
                        .putDocument("level", Document.mapBuilder()
                                .putString("type", "string")
                                .putList("enum", List.of(Document.fromString("PROHIBITED"),
                                        Document.fromString("RESTRICTED"), Document.fromString("ALLOWED")))
                                .build())
                        .putDocument("reason", Document.mapBuilder()
                                .putString("type", "string")
                                .putString("description", "왜 걸렸는지(또는 왜 문제없는지) 1~2문장")
                                .build())
                        .putDocument("penalty", Document.mapBuilder()
                                .putString("type", "string")
                                .putString("description", "발췌문에 과태료 금액이 있을 때만. 없거나 ALLOWED면 생략")
                                .build())
                        .putDocument("action", Document.mapBuilder()
                                .putString("type", "string")
                                .putString("description", "PROHIBITED·RESTRICTED면 필수, ALLOWED면 생략")
                                .build())
                        .build())
                .putList("required", List.of(Document.fromString("level"), Document.fromString("reason")))
                .build();

        return ToolConfiguration.builder()
                .tools(Tool.fromToolSpec(ToolSpecification.builder()
                        .name(TOOL_NAME)
                        .description("물품의 한국 반입 판정을 보고한다")
                        .inputSchema(ToolInputSchema.fromJson(schema))
                        .build()))
                .toolChoice(ToolChoice.fromTool(SpecificToolChoice.builder().name(TOOL_NAME).build()))
                .build();
    }

    private QuarantineVerdict parseVerdict(ConverseResponse response) {
        ToolUseBlock toolUse = response.output().message().content().stream()
                .map(ContentBlock::toolUse)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> {
                    log.error("[quarantine-check] 응답에 도구 사용 블록이 없다 stopReason={}", response.stopReasonAsString());
                    return new IllegalStateException("판정 응답 형식이 올바르지 않습니다.");
                });

        Map<String, Document> fields = toolUse.input().asMap();
        RegulationLevel level = levelOf(textOf(fields.get("level")));
        String reason = textOf(fields.get("reason"));
        String penalty = textOf(fields.get("penalty"));
        String action = textOf(fields.get("action"));

        QuarantineVerdict verdict = QuarantineVerdict.of(level, reason, penalty, action);
        if (verdict.reason() == null) {
            log.warn("[quarantine-check] 판정에 사유가 비어 있다 level={}", level);
        }
        if (level != RegulationLevel.ALLOWED && verdict.action() == null) {
            log.warn("[quarantine-check] 금지·제한 판정인데 조치 방법이 비어 있다 level={}", level);
        }
        return verdict;
    }

    /** 모델이 모르는 level을 적었으면 판정 자체를 못 쓴다 — 추측해서 채우지 않고 실패로 끝낸다. */
    private RegulationLevel levelOf(String value) {
        if (value != null) {
            for (RegulationLevel level : RegulationLevel.values()) {
                if (level.name().equalsIgnoreCase(value.trim())) {
                    return level;
                }
            }
        }
        log.error("[quarantine-check] 해석할 수 없는 level={}", value);
        throw new IllegalStateException("판정 응답의 level을 해석할 수 없습니다.");
    }

    private String textOf(Document document) {
        return document == null || document.isNull() ? null : document.asString();
    }
}
