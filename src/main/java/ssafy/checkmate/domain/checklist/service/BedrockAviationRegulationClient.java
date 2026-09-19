package ssafy.checkmate.domain.checklist.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BedrockAviationRegulationClient implements AviationRegulationClient {

    private static final String SCOPE_ATTRIBUTE = "scope";
    private static final String SCOPE_VALUE = "avsec";
    private static final String JURISDICTIONS_ATTRIBUTE = "jurisdictions";
    private static final String TOOL_NAME = "report_verdicts";
    /** 묶음 하나당 60~80토큰으로 보고 잡는 여유분. 응답이 잘리면 파싱 자체가 실패한다. */
    private static final int TOKENS_PER_TARGET = 80;
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
            주어진 규정 발췌문만 근거로 삼아, 물품 목록의 각 줄이 반입 가능한지 판정하세요.

            판정 기준 (물품이 실제로 담긴 가방 구분을 기준으로 판정할 것)
            - VIOLATION: 그 가방에 넣으면 반입이 불가한 물품.
              예) 기내 가방의 칼, 위탁 가방의 보조배터리.
            - WARNING: 반입 자체는 되지만 조건(용량·개수·포장 등)을 지켜야 하는 물품.
            - NONE: 제한 없이 반입 가능한 물품.

            판정과 guide는 반드시 일치해야 합니다. guide에 "반입이 불가"라고 쓸 거라면
            판정은 WARNING이 아니라 VIOLATION입니다.

            반드시 지킬 것
            - 물품 목록의 모든 줄을 빠짐없이 판정하세요. NONE인 줄도 절대 생략하지 마세요.
            - 각 줄의 itemId를 그대로 돌려주세요. 새로 만들거나 번호를 다시 매기지 마세요.
            - carriageType이 "미지정"이면 기내와 위탁 어느 쪽인지 알 수 없으므로,
              둘 다 고려해 더 보수적인 쪽으로 판정하세요.
            - 규정 발췌문에 근거가 없으면 NONE으로 두세요. 추측하지 마세요.

            guide 작성 규칙
            - NONE이면 guide를 비우세요.
            - VIOLATION/WARNING이면 규정 원문을 그대로 옮기지 말고 다시 쓰세요.
              ① 왜 걸렸는지(기준·수치를 포함) → ② 어떻게 하면 되는지(조치 방법) 순서로,
              한국어 "~해요" 말투의 1~2문장으로 씁니다.
              예) "날 길이가 6cm를 넘는 도검류는 기내 반입이 불가해요. 위탁 수하물로 옮기면 반입할 수 있어요."
            - 조치할 방법이 없으면 한 문장으로 끝내세요.
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
     * 검색어에는 한국어 이름뿐 아니라 <b>그 나라 말로 된 별칭</b>도 함께 싣는다
     * ({@link SecurityCheckTarget#searchTerms()}). 규정 문서가 현지어라 한국어만으로는
     * 임베딩이 매칭하지 못해 규정을 통째로 놓친다 — 예를 들어 "가위"만으로는 일본어 코퍼스의
     * {@code はさみ} 규정이 20위 안에 안 잡히는데, 별칭을 붙이면 2위로 올라온다.
     *
     * 같은 청크가 여러 물품에서 겹쳐 나오므로 본문 기준으로 중복을 제거한다.
     */
    String retrieveRegulations(CountryJurisdiction jurisdiction, List<SecurityCheckTarget> targets) {
        RetrievalFilter filter = filterFor(jurisdiction);
        List<String> queries = targets.stream()
                .map(SecurityCheckTarget::searchTerms)
                .distinct()
                .toList();

        Set<String> chunks = new LinkedHashSet<>();
        queries.forEach(query -> chunks.addAll(retrieveFor(query, filter)));

        log.info("[security-check] 규정 검색 완료 jurisdiction={} 물품={}종 청크={}건(중복 제거 후)",
                jurisdiction, queries.size(), chunks.size());

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

        return response.retrievalResults().stream()
                .map(result -> result.content().text())
                .toList();
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

        log.info("[security-check] 판정 생성 완료 model={} 묶음={}건 입력토큰={} 출력토큰={}",
                properties.modelId(), targets.size(),
                response.usage().inputTokens(), response.usage().outputTokens());

        return parseVerdicts(response);
    }

    String userPrompt(String regulations, List<SecurityCheckTarget> targets) {
        String itemLines = targets.stream()
                .map(target -> "- itemId=%d | 물품=%s | 가방=%s | 구분=%s".formatted(
                        target.getItemId(), target.getName(), target.getBagName(), target.getCarriageType()))
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
        Document verdictItem = Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", Document.mapBuilder()
                        .putDocument("itemId", Document.mapBuilder()
                                .putString("type", "integer")
                                .putString("description", "물품 목록에 적힌 itemId를 그대로 적는다")
                                .build())
                        .putDocument("level", Document.mapBuilder()
                                .putString("type", "string")
                                .putList("enum", List.of(Document.fromString("NONE"),
                                        Document.fromString("WARNING"), Document.fromString("VIOLATION")))
                                .build())
                        .putDocument("guide", Document.mapBuilder()
                                .putString("type", "string")
                                .putString("description", "NONE이면 비운다. 그 외에는 사유와 조치 방법을 담은 1~2문장")
                                .build())
                        .build())
                .putList("required", List.of(Document.fromString("itemId"), Document.fromString("level")))
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
                        .description("물품 목록의 모든 줄에 대한 반입 판정을 보고한다")
                        .inputSchema(ToolInputSchema.fromJson(schema))
                        .build()))
                .toolChoice(ToolChoice.fromTool(SpecificToolChoice.builder().name(TOOL_NAME).build()))
                .build();
    }

    private List<SecurityVerdict> parseVerdicts(ConverseResponse response) {
        ToolUseBlock toolUse = response.output().message().content().stream()
                .map(ContentBlock::toolUse)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> {
                    log.error("[security-check] 응답에 도구 사용 블록이 없다 stopReason={}", response.stopReasonAsString());
                    return new IllegalStateException("판정 응답 형식이 올바르지 않습니다.");
                });

        List<Document> rawVerdicts = toolUse.input().asMap().get("verdicts").asList();
        List<SecurityVerdict> verdicts = new ArrayList<>();
        rawVerdicts.forEach(raw -> toVerdict(raw).ifPresent(verdicts::add));
        return verdicts;
    }

    /** 모델이 모르는 level을 적었거나 itemId가 숫자가 아니면 그 줄만 버린다 — 나머지는 살린다. */
    private Optional<SecurityVerdict> toVerdict(Document raw) {
        Map<String, Document> fields = raw.asMap();
        Optional<VerdictLevel> level = VerdictLevel.from(textOf(fields.get("level")));
        if (level.isEmpty() || fields.get("itemId") == null) {
            log.warn("[security-check] 해석할 수 없는 판정 줄 — 무시한다 raw={}", raw);
            return Optional.empty();
        }
        return Optional.of(new SecurityVerdict(
                fields.get("itemId").asNumber().longValue(), level.get(), textOf(fields.get("guide"))));
    }

    private String textOf(Document document) {
        return document == null || document.isNull() ? null : document.asString();
    }
}
