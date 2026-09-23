package ssafy.checkmate.domain.checklist.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.global.config.BedrockProperties;

/**
 * Bedrock Knowledge Base(S3 Vectors) 기반 구현.
 *
 * 검색(Retrieve)으로 국가·공통 규정 청크를 모으고, 생성(Converse)으로 묶음마다 판정과 안내
 * 문구를 받는다. RetrieveAndGenerate 한 방이 아니라 두 단계로 나눈 이유는 Converse의 도구
 * 사용(tool use)으로 <b>출력 스키마를 강제</b>할 수 있어서다 — 판정을 텍스트로 받아 파싱하면
 * 형식이 깨질 수 있는데, 스키마를 강제하면 그 실패 모드가 사라진다.
 *
 * 검색 필터에는 국가 코드와 함께 {@code GLOBAL}을 넣는다. IATA 리튬배터리 지침처럼 모든
 * 국가에 공통으로 적용되는 문서가 거기 있어서, 국가 코드만 걸면 보조배터리 판정에서 통째로
 * 빠지기 때문이다.
 *
 * <b>Cohere Rerank는 쓰지 않는다.</b> top-{@code numberOfResults} 벡터 검색 결과를 그대로
 * 넘긴다 — top-30→10으로 줄여서 시도해봤지만, 배치(여행 전체를 한 판정으로 묶는) 구조라
 * rerank 요금이 아낀 토큰값과 비슷해 비용 절감이 거의 없었다. 도입/제거 과정과 실측은
 * RAG_검색품질_모델선정_리포트.md 참고.
 *
 * 판정은 물품이 실제로 담긴 가방을 LLM에게 알려주지 않고, 기내(cabin)·위탁(checked) 각각의
 * 판정을 따로 받은 뒤 <b>서버 코드가</b> 실제 가방에 맞춰 최종 등급 하나로 접는다. 가방
 * 정보와 최종 등급 계산을 한 번에 LLM에게 맡기면 같은 물품·같은 규정인데도 판정이 오가는
 * 흔들림이 있었다(RAG_검색품질_모델선정_리포트.md 3·4·5번).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BedrockAviationRegulationClient implements AviationRegulationClient {

    private static final String SCOPE_ATTRIBUTE = "scope";
    private static final String SCOPE_VALUE = "avsec";
    private static final String JURISDICTIONS_ATTRIBUTE = "jurisdictions";
    private static final String TOOL_NAME = "report_carriage_verdicts";
    private static final String CABIN = "기내";
    private static final String CHECKED = "위탁";
    /** ALLOWED/NO_EVIDENCE보다 CONDITIONAL이, CONDITIONAL보다 PROHIBITED가 더 엄격하다. */
    private static final List<String> CARRIAGE_STRICTNESS =
            List.of("ALLOWED", "NO_EVIDENCE", "CONDITIONAL", "PROHIBITED");
    /**
     * 묶음 하나당 잡는 토큰 여유분. cabin/checked 등급과 안내 문구 두 벌을 받으므로 예전
     * 3분류(80토큰) 대비 넉넉히 잡는다 — 실측(PromptSchemaFullScaleManualIT, 230쌍)에서
     * 물품 하나에 최대 334토큰까지 나왔다. 응답이 잘리면 파싱 자체가 실패한다.
     */
    private static final int TOKENS_PER_TARGET = 200;
    /**
     * 반입 판정은 분류 작업이라 다양성이 이득이 아니라 노이즈다. 같은 여행을 두 번 검사했는데
     * 판정이 "금지"에서 "통과"로 바뀌면 안전 기능으로서 신뢰를 잃는다.
     *
     * <b>주의</b>: Sonnet 5 이상 최신 세대는 샘플링 파라미터가 제거돼 이 값을 보내면 400이
     * 난다. 모델을 올릴 때 함께 빼야 한다.
     */
    private static final float DETERMINISTIC_TEMPERATURE = 0.0f;

    /** 같은 패키지의 프롬프트 덤프 테스트가 실제로 나가는 문구를 그대로 확인한다. */
    static final String SYSTEM_PROMPT = """
            당신은 항공 보안 검색 규정을 안내하는 전문가입니다.
            주어진 규정 발췌문만 근거로 삼아, 물품 목록의 각 물품을 기내 휴대(cabin)와 위탁 수하물(checked)로
            각각 판정하세요. 여행자가 그 물품을 어느 가방에 넣었는지는 고려하지 마세요.

            값
            - ALLOWED: 제한 없이 가능.
            - CONDITIONAL: 발췌문에 명시된 조건(용량·길이·개수·포장·항공사 승인 등)을 지켜야 가능.
            - PROHIBITED: 불가.
            - NO_EVIDENCE: 발췌문에 그 물품이나 그 분류에 대한 근거가 없음. 추측으로 ALLOWED를 쓰지 마세요.

            반드시 지킬 것
            - 물품 목록의 모든 줄을 빠짐없이 판정하고, 각 줄의 itemId를 그대로 돌려주세요.
            - 조건은 발췌문에 명시된 제한만 해당합니다. 일반적인 권고나 검색 절차(꺼내서 검사받기 등)는 조건이
              아닙니다 — 그런 것만 있으면 ALLOWED입니다.
            - 판정과 안내가 일치해야 합니다. 안내에 "불가"라고 쓸 거라면 그 칸은 PROHIBITED입니다.

            안내 작성 규칙 (cabinGuide, checkedGuide)
            - 그 칸이 ALLOWED나 NO_EVIDENCE면 비우세요.
            - CONDITIONAL·PROHIBITED면 ① 왜 걸리는지(기준·수치 포함) → ② 어떻게 하면 되는지 순서로,
              한국어 "~해요" 말투의 1~2문장으로 쓰세요. 다른 가방으로 옮기면 해결되면 그렇게 안내하세요.
              예) cabinGuide: "날 길이가 6cm를 넘는 칼은 기내 반입이 불가해요. 위탁 수하물로 옮기면 반입할 수 있어요."
            - 조문 번호·출처 표기·긴 해설은 넣지 마세요.
            """;

    private final BedrockAgentRuntimeClient bedrockAgentRuntimeClient;
    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final BedrockProperties properties;

    @Override
    public List<SecurityVerdict> judge(CountryJurisdiction jurisdiction, List<SecurityCheckTarget> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        String regulations = retrieveRegulations(jurisdiction, targets);
        return generateVerdicts(regulations, targets);
    }

    /**
     * 물품 <b>이름마다 따로</b> 검색해서 청크를 모은다.
     *
     * 이름을 전부 이어 붙여 한 번만 검색하면 쿼리가 희석돼 특정 물품의 규정이 상위 결과에서
     * 밀려난다 — 실제로 맥가이버칼을 단독으로 물으면 금지인데, 5종을 한 번에 물으면 칼 관련
     * 청크가 빠져 통과로 나왔다. 안전 기능에서 금지 물품을 놓치는 건 허용할 수 없어 검색만
     * 쪼갠다. 검색은 임베딩 1회 + 벡터 쿼리라 저렴하고, 생성(Converse)은 그대로 한 번이다.
     *
     * 검색어는 <b>현지어를 쓴다</b>(한국행은 이름이 곧 현지어). 규정 문서가 현지어라 한국어로는
     * 임베딩이 매칭하지 못해 규정을 통째로 놓친다 — 예를 들어 "가위"만으로는 일본어 코퍼스의
     * {@code はさみ} 규정이 20위 안에 안 잡히는데, 현지어면 잡힌다. 옮기는 일은
     * {@link ItemTranslationClient}가 검색 직전에 한다.
     *
     * <b>검색어가 빈 물품은 그 물품만 건너뛴다.</b> 번역이 실패한 물품인데(§11-5) 한국어로
     * 대체해서 검색해도 결과가 안 좋다는 게 실측으로 확인됐다
     * (RAG_검색품질_모델선정_리포트.md §7·§9-5) — 안 되는 검색에 비용만 쓰느니 건너뛰는 게
     * 낫다. 건너뛴 물품은 청크가 하나도 안 붙어 판정 단계에서 "근거 없음 → NONE" 규칙대로
     * 처리된다.
     *
     * 같은 청크가 여러 물품에서 겹쳐 나오므로 본문 기준으로 중복을 제거한다.
     */
    String retrieveRegulations(CountryJurisdiction jurisdiction, List<SecurityCheckTarget> targets) {
        RetrievalFilter filter = filterFor(jurisdiction);
        List<String> queries = targets.stream()
                .map(SecurityCheckTarget::searchTerms)
                .filter(query -> !query.isBlank())
                .distinct()
                .toList();

        List<String> skipped = targets.stream()
                .filter(target -> target.searchTerms().isBlank())
                .map(SecurityCheckTarget::getName)
                .distinct()
                .toList();
        if (!skipped.isEmpty()) {
            log.warn("[security-check] 검색어가 없어 검색을 건너뛴 물품 — 근거 없이 통과로 판정될 수 있다 물품={}",
                    skipped);
        }

        long startedAt = System.nanoTime();
        Set<String> chunks = new LinkedHashSet<>();
        queries.forEach(query -> chunks.addAll(retrieveFor(query, filter)));

        log.info("[security-check] 규정 검색 완료 jurisdiction={} 물품={}종(검색 {}종) 청크={}건(중복 제거 후) 소요={}ms",
                jurisdiction, targets.stream().map(SecurityCheckTarget::getName).distinct().count(),
                queries.size(), chunks.size(), elapsedMillisSince(startedAt));

        return String.join("\n\n---\n\n", chunks);
    }

    private List<String> retrieveFor(String searchTerms, RetrievalFilter filter) {
        RetrieveResponse response = bedrockAgentRuntimeClient.retrieve(RetrieveRequest.builder()
                .knowledgeBaseId(properties.knowledgeBaseId())
                .retrievalQuery(KnowledgeBaseQuery.builder()
                        .text(searchTerms + " 기내 반입 위탁 수하물 제한")
                        .build())
                .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                        .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                .numberOfResults(properties.numberOfResults())
                                .filter(filter)
                                .build())
                        .build())
                .build());

        List<String> texts = response.retrievalResults().stream()
                .map(result -> result.content().text())
                .toList();
        if (texts.isEmpty()) {
            log.warn("[security-check] 규정 검색 결과가 없다 — 이 물품은 근거 없이 통과로 판정될 수 있다 검색어={}",
                    searchTerms);
        }
        return texts;
    }

    /** scope=avsec 이면서 해당 국가 또는 공통(GLOBAL) 문서만 본다. */
    private RetrievalFilter filterFor(CountryJurisdiction jurisdiction) {
        List<RetrievalFilter> jurisdictionFilters = jurisdiction.filterCodes().stream()
                .map(code -> RetrievalFilter.fromListContains(attribute(JURISDICTIONS_ATTRIBUTE, code)))
                .toList();

        return RetrievalFilter.fromAndAll(List.of(
                RetrievalFilter.fromEqualsValue(attribute(SCOPE_ATTRIBUTE, SCOPE_VALUE)),
                RetrievalFilter.fromOrAll(jurisdictionFilters)));
    }

    private FilterAttribute attribute(String key, String value) {
        return FilterAttribute.builder().key(key).value(Document.fromString(value)).build();
    }

    /** 도구 사용으로 출력 스키마를 강제해 판정을 받는다. */
    private List<SecurityVerdict> generateVerdicts(String regulations, List<SecurityCheckTarget> targets) {
        long startedAt = System.nanoTime();
        ConverseResponse response = bedrockRuntimeClient.converse(ConverseRequest.builder()
                .modelId(properties.modelId())
                .system(SystemContentBlock.fromText(SYSTEM_PROMPT))
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(userPrompt(regulations, targets)))
                        .build())
                .toolConfig(verdictToolConfig())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(maxTokensFor(targets))
                        .temperature(DETERMINISTIC_TEMPERATURE)
                        .build())
                .build());

        log.info("[security-check] 판정 생성 완료 model={} 묶음={}건 입력토큰={} 출력토큰={} stopReason={} 소요={}ms",
                properties.modelId(), targets.size(),
                response.usage().inputTokens(), response.usage().outputTokens(),
                response.stopReasonAsString(), elapsedMillisSince(startedAt));
        if (response.stopReason() == StopReason.MAX_TOKENS) {
            log.warn("[security-check] 응답이 토큰 상한에서 잘렸다 — 뒤쪽 묶음은 판정이 빠질 수 있다 묶음={}건", targets.size());
        }

        return parseVerdicts(response, targets);
    }

    private static long elapsedMillisSince(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    /** 가방 정보를 주지 않는다 — LLM은 기내/위탁 사실만 판정하고, 가방에 맞춘 최종 등급은 코드가 고른다. */
    String userPrompt(String regulations, List<SecurityCheckTarget> targets) {
        String itemLines = targets.stream()
                .map(target -> "- itemId=%d | 물품=%s".formatted(target.getItemId(), target.getName()))
                .collect(Collectors.joining("\n"));

        return """
                [규정 발췌문]
                %s

                [물품 목록] (총 %d줄 — 모두 판정할 것)
                %s
                """.formatted(regulations, targets.size(), itemLines);
    }

    private int maxTokensFor(List<SecurityCheckTarget> targets) {
        return Math.max(properties.maxTokens(), targets.size() * TOKENS_PER_TARGET);
    }

    private ToolConfiguration verdictToolConfig() {
        List<Document> carriageValues = List.of(Document.fromString("ALLOWED"), Document.fromString("CONDITIONAL"),
                Document.fromString("PROHIBITED"), Document.fromString("NO_EVIDENCE"));
        Document verdictItem = Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", Document.mapBuilder()
                        .putDocument("itemId", Document.mapBuilder()
                                .putString("type", "integer")
                                .putString("description", "물품 목록에 적힌 itemId를 그대로 적는다")
                                .build())
                        .putDocument("cabin", Document.mapBuilder()
                                .putString("type", "string")
                                .putList("enum", carriageValues)
                                .putString("description", "기내 휴대 판정")
                                .build())
                        .putDocument("checked", Document.mapBuilder()
                                .putString("type", "string")
                                .putList("enum", carriageValues)
                                .putString("description", "위탁 수하물 판정")
                                .build())
                        .putDocument("cabinGuide", Document.mapBuilder()
                                .putString("type", "string")
                                .putString("description", "기내에 넣을 때의 안내. ALLOWED·NO_EVIDENCE면 비운다")
                                .build())
                        .putDocument("checkedGuide", Document.mapBuilder()
                                .putString("type", "string")
                                .putString("description", "위탁에 넣을 때의 안내. ALLOWED·NO_EVIDENCE면 비운다")
                                .build())
                        .build())
                .putList("required", List.of(Document.fromString("itemId"), Document.fromString("cabin"),
                        Document.fromString("checked")))
                .build();

        Document schema = Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", Document.mapBuilder()
                        .putDocument("verdicts", Document.mapBuilder()
                                .putString("type", "array")
                                .putDocument("items", verdictItem)
                                .build())
                        .build())
                .putList("required", List.of(Document.fromString("verdicts")))
                .build();

        return ToolConfiguration.builder()
                .tools(Tool.fromToolSpec(ToolSpecification.builder()
                        .name(TOOL_NAME)
                        .description("물품마다 기내·위탁 반입 판정을 보고한다")
                        .inputSchema(ToolInputSchema.fromJson(schema))
                        .build()))
                .toolChoice(ToolChoice.fromTool(SpecificToolChoice.builder().name(TOOL_NAME).build()))
                .build();
    }

    /**
     * LLM이 물품마다 돌려준 cabin/checked 판정을, 그 물품이 실제로 담긴 가방
     * ({@link SecurityCheckTarget#getCarriageType()})에 맞춰 최종 등급 하나로 접는다.
     * 가방이 "미지정"이면 둘 중 더 엄격한 쪽을 쓴다({@link #CARRIAGE_STRICTNESS}).
     */
    private List<SecurityVerdict> parseVerdicts(ConverseResponse response, List<SecurityCheckTarget> targets) {
        ToolUseBlock toolUse = response.output().message().content().stream()
                .map(ContentBlock::toolUse)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> {
                    log.error("[security-check] 응답에 도구 사용 블록이 없다 stopReason={}", response.stopReasonAsString());
                    return new IllegalStateException("판정 응답 형식이 올바르지 않습니다.");
                });

        Map<Long, SecurityCheckTarget> targetsByItemId = targets.stream()
                .collect(Collectors.toMap(SecurityCheckTarget::getItemId, target -> target, (a, b) -> a));

        List<Document> rawVerdicts = toolUse.input().asMap().get("verdicts").asList();
        List<SecurityVerdict> verdicts = new ArrayList<>();
        rawVerdicts.forEach(raw -> toVerdict(raw, targetsByItemId).ifPresent(verdicts::add));
        return verdicts;
    }

    /** 모델이 모르는 값을 적었거나 itemId를 찾을 수 없으면 그 줄만 버린다 — 나머지는 살린다. */
    private Optional<SecurityVerdict> toVerdict(Document raw, Map<Long, SecurityCheckTarget> targetsByItemId) {
        Map<String, Document> fields = raw.asMap();
        String cabin = textOf(fields.get("cabin"));
        String checked = textOf(fields.get("checked"));
        if (fields.get("itemId") == null || !CARRIAGE_STRICTNESS.contains(cabin) || !CARRIAGE_STRICTNESS.contains(checked)) {
            log.warn("[security-check] 해석할 수 없는 판정 줄 — 무시한다 raw={}", raw);
            return Optional.empty();
        }

        long itemId = fields.get("itemId").asNumber().longValue();
        SecurityCheckTarget target = targetsByItemId.get(itemId);
        if (target == null) {
            log.warn("[security-check] 물품 목록에 없는 itemId — 무시한다 itemId={}", itemId);
            return Optional.empty();
        }

        String cabinGuide = textOf(fields.get("cabinGuide"));
        String checkedGuide = textOf(fields.get("checkedGuide"));
        String resolvedCarriage = resolveCarriage(target.getCarriageType(), cabin, checked);
        String carriageValue = CABIN.equals(resolvedCarriage) ? cabin : checked;
        VerdictLevel level = toVerdictLevel(carriageValue);
        String guide = level == VerdictLevel.NONE ? null
                : guideFor(resolvedCarriage, cabin, checked, cabinGuide, checkedGuide);
        return Optional.of(new SecurityVerdict(itemId, level, guide));
    }

    /** carriageType이 "미지정"이면 cabin/checked 중 더 엄격한 쪽을 실제 가방으로 취급한다. */
    private String resolveCarriage(String carriageType, String cabin, String checked) {
        if (CABIN.equals(carriageType) || CHECKED.equals(carriageType)) {
            return carriageType;
        }
        return CARRIAGE_STRICTNESS.indexOf(cabin) >= CARRIAGE_STRICTNESS.indexOf(checked) ? CABIN : CHECKED;
    }

    private VerdictLevel toVerdictLevel(String carriageValue) {
        return switch (carriageValue) {
            case "PROHIBITED" -> VerdictLevel.VIOLATION;
            case "CONDITIONAL" -> VerdictLevel.WARNING;
            default -> VerdictLevel.NONE; // ALLOWED, NO_EVIDENCE
        };
    }

    /**
     * 실제(또는 미지정이면 더 엄격한 쪽으로 정한) 가방 쪽 안내를 기본으로 쓰고, 다른 가방이었다면
     * 등급이 달라진다는 점을 한 줄 덧붙인다(예: 칼을 위탁에 넣으면 "" + "기내 가방으로는 옮기지 마세요").
     */
    private String guideFor(String resolvedCarriage, String cabin, String checked, String cabinGuide, String checkedGuide) {
        boolean cabinSide = CABIN.equals(resolvedCarriage);
        String ownValue = cabinSide ? cabin : checked;
        String otherValue = cabinSide ? checked : cabin;
        String own = cabinSide ? cabinGuide : checkedGuide;
        String other = cabinSide ? checkedGuide : cabinGuide;
        if (ownValue.equals(otherValue) || other == null || other.isBlank()) {
            return own;
        }
        String otherBag = cabinSide ? "위탁 수하물" : "기내 가방";
        String prefix = own == null || own.isBlank() ? "지금 가방에 넣었다면 문제없어요. " : own + " ";
        return prefix + "(%s에 넣을 경우) %s".formatted(otherBag, other);
    }

    private String textOf(Document document) {
        return document == null || document.isNull() ? null : document.asString();
    }
}
