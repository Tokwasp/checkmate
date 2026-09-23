package ssafy.checkmate.domain.checklist.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockRerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockRerankingModelConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.FilterAttribute;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocumentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQueryContentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSource;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSourceType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankTextDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfigurationType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
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
 * 판정 출력 형식 비교(30번) — 지금의 3분류(가방을 보고 LLM이 등급 결정) vs 기내·위탁 분리(LLM은 사실만,
 * 등급은 코드가 가방으로 계산).
 *
 * 검색은 둘 다 같다: 벡터 top-30 → Cohere Rerank 3.5(현지어 이름) → top-10. 케이스마다 검색을 한 번만 하고
 * 그 규정 본문으로 두 프롬프트를 각각 {@link #REPEATS}회 판정한다 — 차이가 프롬프트에서만 나오게.
 * 지금 프롬프트는 가방=기내백(기내)으로 판정받는다(230쌍 측정과 같은 조건).
 *
 * <b>실제로 과금된다(약 1,500원 예상).</b> 추정 비용이 {@link #BUDGET_WON}을 넘으면 멈춘다.
 * 실행: {@code RERANK_PROMPT=true ./gradlew test --rerun
 *        --tests "ssafy.checkmate.domain.checklist.service.PromptSchemaComparisonManualIT"}
 * 결과는 {@code build/prompt-schema-comparison.md}에 남는다.
 */
@Disabled("실호출·과금 테스트 — 돌릴 때만 이 줄을 지우고 RERANK_PROMPT=true로 실행")
@EnabledIfEnvironmentVariable(named = "RERANK_PROMPT", matches = "true")
@SpringBootTest
class PromptSchemaComparisonManualIT {

    /** 흔들린 6건 + 칼 3건 + 과잉 경고 확인 2건. */
    private static final List<Case> CASES = List.of(
            new Case(CountryJurisdiction.TH, "보조배터리"),
            new Case(CountryJurisdiction.PH, "보조배터리"),
            new Case(CountryJurisdiction.IT, "상비약"),
            new Case(CountryJurisdiction.JP, "텀블러"),
            new Case(CountryJurisdiction.IT, "가위"),
            new Case(CountryJurisdiction.FR, "가위"),
            new Case(CountryJurisdiction.TW, "칼"),
            new Case(CountryJurisdiction.FR, "칼"),
            new Case(CountryJurisdiction.VN, "칼"),
            new Case(CountryJurisdiction.JP, "노트북"),
            new Case(CountryJurisdiction.US, "충전기"));

    private static final int REPEATS = 3;
    private static final String QUERY_SUFFIX = " 기내 반입 위탁 수하물 제한";
    private static final int POOL_SIZE = 30;
    private static final int RERANK_TOP = 10;
    private static final String COHERE_MODEL_ARN = "arn:aws:bedrock:ap-northeast-1::foundation-model/cohere.rerank-v3-5:0";
    private static final String NEW_TOOL_NAME = "report_carriage_verdicts";

    private static final double INPUT_USD = 3.0 / 1_000_000;
    private static final double OUTPUT_USD = 15.0 / 1_000_000;
    private static final double RERANK_USD = 2.0 / 1_000;
    private static final double WON_PER_USD = 1_400;
    private static final double BUDGET_WON = 3_000;

    static final String NEW_SYSTEM_PROMPT = """
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

    @Autowired
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @Autowired
    private BedrockRuntimeClient bedrockRuntimeClient;

    @Autowired
    private BedrockProperties properties;

    @Autowired
    private ItemTranslationClient itemTranslationClient;

    @Autowired
    private BedrockAviationRegulationClient aviationClient;

    private double spentWon;

    @Test
    @DisplayName("같은 검색 결과로 지금 프롬프트(3분류)와 기내·위탁 분리 프롬프트를 3회씩 판정해 흔들림을 비교한다")
    void compare() throws Exception {
        Map<CountryJurisdiction, List<String>> itemsByCountry = CASES.stream()
                .collect(Collectors.groupingBy(Case::country, LinkedHashMap::new,
                        Collectors.mapping(Case::item, Collectors.toList())));
        Map<CountryJurisdiction, Map<String, String>> terms = new LinkedHashMap<>();
        itemsByCountry.forEach((country, items) -> terms.put(country, itemTranslationClient.translate(items, country)));

        StringBuilder summary = new StringBuilder("# 판정 출력 형식 비교 — 지금(3분류) vs 기내·위탁 분리\n\n")
                .append("검색: 벡터 top-30 → Cohere top-10 (케이스마다 한 번, 두 프롬프트 공통) / 판정 모델=")
                .append(properties.modelId()).append(" / 각 ").append(REPEATS).append("회\n\n")
                .append("| 국가-물품 | 지금: 기내백 등급 (3회) | 새: cabin (3회) | 새: checked (3회) | 새→기내 | 새→위탁 | 새→미지정 |\n")
                .append("|---|---|---|---|---|---|---|\n");
        StringBuilder details = new StringBuilder("\n# 안내 문구 (1회차)\n");

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (Case testCase : CASES) {
                String term = terms.get(testCase.country()).getOrDefault(testCase.item(), "");
                if (term.isBlank()) {
                    summary.append("| %s | 번역 실패 | | | | | |\n".formatted(testCase.label()));
                    continue;
                }
                List<String> pool = retrieve(term + QUERY_SUFFIX, testCase.country());
                String regulations = rerankTop(tokyo, term, pool);
                spentWon += RERANK_USD * WON_PER_USD;

                List<String> oldLevels = new ArrayList<>();
                List<NewVerdict> newVerdicts = new ArrayList<>();
                String oldGuide = "-";
                for (int round = 0; round < REPEATS; round++) {
                    guardBudget();
                    SecurityCheckTarget target = new SecurityCheckTarget(1L, testCase.item(), "기내백", "기내", term);
                    ConverseResponse oldResponse = converseOld(regulations, target);
                    SecurityVerdict old = firstOldVerdict(oldResponse);
                    oldLevels.add(old == null ? "없음" : label(old.getLevel().name()));
                    if (round == 0 && old != null && old.getGuide() != null) {
                        oldGuide = old.getGuide();
                    }
                    newVerdicts.add(parseNew(converseNew(regulations, testCase.item())));
                }

                summary.append("| %s | %s | %s | %s | %s | %s | %s |\n".formatted(testCase.label(),
                        String.join("/", oldLevels),
                        newVerdicts.stream().map(v -> v.cabin).collect(Collectors.joining("/")),
                        newVerdicts.stream().map(v -> v.checked).collect(Collectors.joining("/")),
                        newVerdicts.stream().map(v -> label(v.levelFor("기내"))).collect(Collectors.joining("/")),
                        newVerdicts.stream().map(v -> label(v.levelFor("위탁"))).collect(Collectors.joining("/")),
                        newVerdicts.stream().map(v -> label(v.levelFor("미지정"))).collect(Collectors.joining("/"))));

                NewVerdict first = newVerdicts.get(0);
                details.append("\n## %s (검색어: %s)\n\n".formatted(testCase.label(), term))
                        .append("- 지금(기내백): ").append(oneLine(oldGuide)).append("\n")
                        .append("- 새 cabinGuide: ").append(oneLine(first.cabinGuide)).append("\n")
                        .append("- 새 checkedGuide: ").append(oneLine(first.checkedGuide)).append("\n")
                        .append("- 새 → 기내백 사용자에게: ").append(oneLine(first.guideFor("기내"))).append("\n")
                        .append("- 새 → 위탁 사용자에게: ").append(oneLine(first.guideFor("위탁"))).append("\n");
            }
        }

        summary.append("\n추정 비용: %.0f원\n".formatted(spentWon));
        Path out = Path.of("build", "prompt-schema-comparison.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, summary.append(details).toString());
        System.out.println(summary);
    }

    // ---- 검색 (운영·230쌍 측정과 같음) ----

    private List<String> retrieve(String query, CountryJurisdiction country) {
        List<RetrievalFilter> jurisdictions = country.filterCodes().stream()
                .map(code -> RetrievalFilter.fromListContains(attribute("jurisdictions", code)))
                .toList();
        RetrievalFilter filter = RetrievalFilter.fromAndAll(List.of(
                RetrievalFilter.fromEqualsValue(attribute("scope", "avsec")),
                RetrievalFilter.fromOrAll(jurisdictions)));
        return bedrockAgentRuntimeClient.retrieve(RetrieveRequest.builder()
                        .knowledgeBaseId(properties.knowledgeBaseId())
                        .retrievalQuery(KnowledgeBaseQuery.builder().text(query).build())
                        .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                                .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                        .numberOfResults(POOL_SIZE).filter(filter).build())
                                .build())
                        .build())
                .retrievalResults().stream()
                .map(result -> result.content().text())
                .toList();
    }

    private FilterAttribute attribute(String key, String value) {
        return FilterAttribute.builder().key(key).value(Document.fromString(value)).build();
    }

    private String rerankTop(BedrockAgentRuntimeClient tokyo, String query, List<String> pool) {
        List<RerankSource> sources = pool.stream()
                .map(text -> RerankSource.builder()
                        .type(RerankSourceType.INLINE)
                        .inlineDocumentSource(RerankDocument.builder()
                                .type(RerankDocumentType.TEXT)
                                .textDocument(RerankTextDocument.builder().text(text).build())
                                .build())
                        .build())
                .toList();
        return tokyo.rerank(RerankRequest.builder()
                        .queries(RerankQuery.builder()
                                .type(RerankQueryContentType.TEXT)
                                .textQuery(RerankTextDocument.builder().text(query).build())
                                .build())
                        .sources(sources)
                        .rerankingConfiguration(RerankingConfiguration.builder()
                                .type(RerankingConfigurationType.BEDROCK_RERANKING_MODEL)
                                .bedrockRerankingConfiguration(BedrockRerankingConfiguration.builder()
                                        .numberOfResults(Math.min(RERANK_TOP, pool.size()))
                                        .modelConfiguration(BedrockRerankingModelConfiguration.builder()
                                                .modelArn(COHERE_MODEL_ARN).build())
                                        .build())
                                .build())
                        .build())
                .results().stream()
                .map(result -> pool.get(result.index()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    // ---- 지금 프롬프트 (운영과 같은 요청) ----

    private ConverseResponse converseOld(String regulations, SecurityCheckTarget target) throws Exception {
        ConverseResponse response = bedrockRuntimeClient.converse(ConverseRequest.builder()
                .modelId(properties.modelId())
                .system(SystemContentBlock.fromText(BedrockAviationRegulationClient.SYSTEM_PROMPT))
                .messages(userMessage(aviationClient.userPrompt(regulations, List.of(target))))
                .toolConfig((ToolConfiguration) invokePrivate("verdictToolConfig"))
                .inferenceConfig(inference())
                .build());
        charge(response);
        return response;
    }

    @SuppressWarnings("unchecked")
    private SecurityVerdict firstOldVerdict(ConverseResponse response) throws Exception {
        List<SecurityVerdict> verdicts = (List<SecurityVerdict>) invokePrivate("parseVerdicts", response);
        return verdicts.isEmpty() ? null : verdicts.get(0);
    }

    // ---- 새 프롬프트: 기내·위탁 분리, 가방 정보 없음 ----

    private ConverseResponse converseNew(String regulations, String item) {
        String userPrompt = """
                [규정 발췌문]
                %s

                [물품 목록] (총 1줄 — 모두 판정할 것)
                - itemId=1 | 물품=%s
                """.formatted(regulations, item);
        ConverseResponse response = bedrockRuntimeClient.converse(ConverseRequest.builder()
                .modelId(properties.modelId())
                .system(SystemContentBlock.fromText(NEW_SYSTEM_PROMPT))
                .messages(userMessage(userPrompt))
                .toolConfig(newToolConfig())
                .inferenceConfig(inference())
                .build());
        charge(response);
        return response;
    }

    private ToolConfiguration newToolConfig() {
        List<Document> values = List.of(Document.fromString("ALLOWED"), Document.fromString("CONDITIONAL"),
                Document.fromString("PROHIBITED"), Document.fromString("NO_EVIDENCE"));
        Document verdictItem = Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", Document.mapBuilder()
                        .putDocument("itemId", Document.mapBuilder().putString("type", "integer").build())
                        .putDocument("cabin", Document.mapBuilder().putString("type", "string")
                                .putList("enum", values).putString("description", "기내 휴대 판정").build())
                        .putDocument("checked", Document.mapBuilder().putString("type", "string")
                                .putList("enum", values).putString("description", "위탁 수하물 판정").build())
                        .putDocument("cabinGuide", Document.mapBuilder().putString("type", "string")
                                .putString("description", "기내에 넣을 때의 안내. ALLOWED·NO_EVIDENCE면 비운다").build())
                        .putDocument("checkedGuide", Document.mapBuilder().putString("type", "string")
                                .putString("description", "위탁에 넣을 때의 안내. ALLOWED·NO_EVIDENCE면 비운다").build())
                        .build())
                .putList("required", List.of(Document.fromString("itemId"), Document.fromString("cabin"),
                        Document.fromString("checked")))
                .build();
        Document schema = Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", Document.mapBuilder()
                        .putDocument("verdicts", Document.mapBuilder()
                                .putString("type", "array").putDocument("items", verdictItem).build())
                        .build())
                .putList("required", List.of(Document.fromString("verdicts")))
                .build();
        return ToolConfiguration.builder()
                .tools(Tool.fromToolSpec(ToolSpecification.builder()
                        .name(NEW_TOOL_NAME)
                        .description("물품마다 기내·위탁 반입 판정을 보고한다")
                        .inputSchema(ToolInputSchema.fromJson(schema))
                        .build()))
                .toolChoice(ToolChoice.fromTool(SpecificToolChoice.builder().name(NEW_TOOL_NAME).build()))
                .build();
    }

    private NewVerdict parseNew(ConverseResponse response) {
        ToolUseBlock toolUse = response.output().message().content().stream()
                .map(ContentBlock::toolUse)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();
        Map<String, Document> fields = toolUse.input().asMap().get("verdicts").asList().get(0).asMap();
        return new NewVerdict(text(fields.get("cabin")), text(fields.get("checked")),
                text(fields.get("cabinGuide")), text(fields.get("checkedGuide")));
    }

    private static String text(Document document) {
        return document == null || document.isNull() ? "" : document.asString();
    }

    // ---- 공통 ----

    private Message userMessage(String text) {
        return Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText(text)).build();
    }

    private InferenceConfiguration inference() {
        return InferenceConfiguration.builder().maxTokens(properties.maxTokens()).temperature(0.0f).build();
    }

    private void charge(ConverseResponse response) {
        spentWon += (response.usage().inputTokens() * INPUT_USD + response.usage().outputTokens() * OUTPUT_USD)
                * WON_PER_USD;
    }

    private void guardBudget() {
        if (spentWon > BUDGET_WON) {
            throw new IllegalStateException("추정 비용이 예산 %.0f원을 넘어 멈춘다".formatted(BUDGET_WON));
        }
    }

    private Object invokePrivate(String name, Object... args) throws Exception {
        Method method = Arrays.stream(BedrockAviationRegulationClient.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == args.length)
                .findFirst()
                .orElseThrow();
        method.setAccessible(true);
        try {
            return method.invoke(aviationClient, args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static String label(String level) {
        return switch (level) {
            case "VIOLATION", "PROHIBITED" -> "금지";
            case "WARNING", "CONDITIONAL" -> "주의";
            case "NONE", "ALLOWED" -> "통과";
            case "NO_EVIDENCE" -> "통과(근거없음)";
            default -> level;
        };
    }

    private static String oneLine(String text) {
        return text == null || text.isBlank() ? "(비움)" : text.replace("\n", " ").replace("|", "/");
    }

    private record Case(CountryJurisdiction country, String item) {

        String label() {
            return country + "-" + item;
        }
    }

    /** 새 형식 한 건. 등급·문구는 가방 구분으로 코드가 정한다. */
    private record NewVerdict(String cabin, String checked, String cabinGuide, String checkedGuide) {

        private static final List<String> STRICTNESS = List.of("ALLOWED", "NO_EVIDENCE", "CONDITIONAL", "PROHIBITED");

        String levelFor(String carriage) {
            return switch (carriage) {
                case "기내" -> cabin;
                case "위탁" -> checked;
                default -> STRICTNESS.indexOf(cabin) >= STRICTNESS.indexOf(checked) ? cabin : checked;
            };
        }

        /** 넣은 가방 쪽 안내 + 다른 가방이면 등급이 달라질 때 그쪽 안내를 덧붙인다. */
        String guideFor(String carriage) {
            boolean cabinSide = carriage.equals("기내");
            String own = cabinSide ? cabinGuide : checkedGuide;
            String other = cabinSide ? checkedGuide : cabinGuide;
            String ownLevel = cabinSide ? cabin : checked;
            String otherLevel = cabinSide ? checked : cabin;
            if (ownLevel.equals(otherLevel) || other.isBlank()) {
                return own;
            }
            String otherBag = cabinSide ? "위탁 수하물" : "기내 가방";
            String prefix = own.isBlank() ? "지금 가방에 넣었다면 문제없어요. " : own + " ";
            return prefix + "(%s에 넣을 경우) %s".formatted(otherBag, other);
        }
    }
}
