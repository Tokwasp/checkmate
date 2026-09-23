package ssafy.checkmate.domain.checklist.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 판정 출력 형식(기내·위탁 분리, {@link PromptSchemaComparisonManualIT#NEW_SYSTEM_PROMPT})을
 * 리포트의 230쌍(10개국 × 23종)과 같은 기준으로 전수 확인하는 테스트.
 *
 * {@link PromptSchemaComparisonManualIT}는 11건 × 3회 샘플로 "형식을 바꾸면 흔들림이
 * 사라지는지"만 확인했다. 이 클래스는 반복 없이(1쌍당 1회) 230쌍 전체를 새 형식으로 판정해
 * 전수 결과표를 만든다.
 *
 * 검색은 리포트 2번(Cohere 리랭크)과 동일: 벡터 top-30 → Cohere Rerank 3.5(현지어 이름 질의)
 * → top-10. 가방 가정은 다른 표와 비교할 수 있도록 기내백(기내)으로 접어서 보여주되, 위탁
 * 판정도 함께 남긴다.
 *
 * <b>실제로 과금된다(약 5,000~7,000원 예상).</b> 누적 추정 비용이 {@link #BUDGET_WON}을
 * 넘으면 즉시 멈춘다.
 * 실행: {@code FULL_SCALE_NEW_FORMAT=true ./gradlew test --rerun
 *        --tests "ssafy.checkmate.domain.checklist.service.PromptSchemaFullScaleManualIT"}
 * 결과는 나라 하나가 끝날 때마다 {@code build/new-format-full-scale-230.md}에 덮어써 남는다.
 */
@Disabled("실호출·과금 테스트 — 돌릴 때만 이 줄을 지우고 FULL_SCALE_NEW_FORMAT=true로 실행")
@EnabledIfEnvironmentVariable(named = "FULL_SCALE_NEW_FORMAT", matches = "true")
@SpringBootTest
class PromptSchemaFullScaleManualIT {

    /** 리포트와 같은 10개국. */
    private static final List<CountryJurisdiction> COUNTRIES = List.of(
            CountryJurisdiction.JP, CountryJurisdiction.CN, CountryJurisdiction.TW, CountryJurisdiction.TH,
            CountryJurisdiction.VN, CountryJurisdiction.PH, CountryJurisdiction.US, CountryJurisdiction.GB,
            CountryJurisdiction.FR, CountryJurisdiction.IT);

    /** AI/classes.txt 의 마스터 23종 — 리포트·CohereRerankVerdictManualIT와 같은 순서. */
    private static final List<String> ITEMS = List.of(
            "여권", "지갑", "노트북", "충전기", "보조배터리", "이어폰", "상의", "하의", "양말", "신발류", "모자",
            "선글라스", "우산", "가위", "칼", "태블릿", "삼각대", "상비약", "장갑", "칫솔", "치약", "텀블러", "백팩");

    private static final String QUERY_SUFFIX = " 기내 반입 위탁 수하물 제한";
    private static final int POOL_SIZE = 30;
    private static final int RERANK_TOP = 10;
    private static final String COHERE_MODEL_ARN = "arn:aws:bedrock:ap-northeast-1::foundation-model/cohere.rerank-v3-5:0";
    private static final String NEW_TOOL_NAME = "report_carriage_verdicts";

    /** 결과표를 기존 표(230쌍, 기내백 가정)와 나란히 볼 수 있도록 접는 기준. */
    private static final String ASSUMED_BAG_CARRIAGE = "기내";

    private static final double INPUT_USD = 3.0 / 1_000_000;
    private static final double OUTPUT_USD = 15.0 / 1_000_000;
    private static final double RERANK_USD = 2.0 / 1_000;
    private static final double WON_PER_USD = 1_400;
    private static final double BUDGET_WON = 8_000;

    private static final Path REPORT = Path.of("build", "new-format-full-scale-230.md");

    @Autowired
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @Autowired
    private BedrockRuntimeClient bedrockRuntimeClient;

    @Autowired
    private BedrockProperties properties;

    @Autowired
    private ItemTranslationClient itemTranslationClient;

    private double spentWon;

    @Test
    @DisplayName("새 출력 형식(기내·위탁 분리)으로 10개국 × 23종(230쌍)을 1회씩 판정한다")
    void judgeAllPairsOnce() throws Exception {
        StringBuilder report = new StringBuilder("# 새 출력 형식(기내·위탁 분리) 전수 판정 — 10개국 × 23종, 1회\n\n")
                .append("판정 모델=").append(properties.modelId())
                .append(" / 검색: 벡터 top-").append(POOL_SIZE).append(" → Cohere top-").append(RERANK_TOP)
                .append(" / 표시 가정 가방=기내백(").append(ASSUMED_BAG_CARRIAGE).append(")\n");

        Map<String, Map<CountryJurisdiction, String>> matrix = new LinkedHashMap<>();
        ITEMS.forEach(item -> matrix.put(item, new LinkedHashMap<>()));

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (CountryJurisdiction country : COUNTRIES) {
                report.append(judgeCountry(tokyo, country, matrix));
                report.append("\n누적 추정 비용: %.0f원\n".formatted(spentWon));
                write(report, matrix);
            }
        }
        report.append("\n## 합계\n\n총 추정 비용: %.0f원\n".formatted(spentWon));
        write(report, matrix);
        System.out.println(report);
    }

    private String judgeCountry(BedrockAgentRuntimeClient tokyo, CountryJurisdiction country,
            Map<String, Map<CountryJurisdiction, String>> matrix) throws Exception {
        Map<String, String> terms = itemTranslationClient.translate(ITEMS, country);
        StringBuilder table = new StringBuilder("\n## %s (%s)\n\n".formatted(country, country.getLanguageName()))
                .append("| 물품 | 현지어 검색어 | 기내 | 위탁 | 기내백 가정 | cabinGuide | checkedGuide | 입력토큰 | 출력토큰 | 비용(원) |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|\n");

        for (String item : ITEMS) {
            String term = terms.getOrDefault(item, "");
            if (term.isBlank()) {
                table.append("| %s | 번역 실패 | - | - | (검색 건너뜀) | - | - | - | - | - |\n".formatted(item));
                matrix.get(item).put(country, "번역실패");
                continue;
            }
            guardBudget();

            List<String> pool = retrieve(term + QUERY_SUFFIX, country);
            String regulations = pool.isEmpty() ? "" : rerankTop(tokyo, term, pool);
            if (!pool.isEmpty()) {
                spentWon += RERANK_USD * WON_PER_USD;
            }

            ConverseResponse response = converseNew(regulations, item);
            charge(response);
            NewVerdict verdict = parseNew(response);
            String assumed = label(verdict.levelFor(ASSUMED_BAG_CARRIAGE));
            matrix.get(item).put(country, assumed);

            table.append("| %s | %s | %s | %s | %s | %s | %s | %d | %d | %.1f |\n".formatted(
                    item, term, label(verdict.cabin), label(verdict.checked), assumed,
                    oneLine(verdict.cabinGuide), oneLine(verdict.checkedGuide),
                    response.usage().inputTokens(), response.usage().outputTokens(),
                    (response.usage().inputTokens() * INPUT_USD + response.usage().outputTokens() * OUTPUT_USD)
                            * WON_PER_USD));
        }
        return table.toString();
    }

    // ---- 검색 (리포트 2번·PromptSchemaComparisonManualIT과 동일) ----

    private List<String> retrieve(String query, CountryJurisdiction country) {
        return bedrockAgentRuntimeClient.retrieve(RetrieveRequest.builder()
                        .knowledgeBaseId(properties.knowledgeBaseId())
                        .retrievalQuery(KnowledgeBaseQuery.builder().text(query).build())
                        .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                                .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                        .numberOfResults(POOL_SIZE)
                                        .filter(filterFor(country))
                                        .build())
                                .build())
                        .build())
                .retrievalResults().stream()
                .map(result -> result.content().text())
                .toList();
    }

    private RetrievalFilter filterFor(CountryJurisdiction country) {
        List<RetrievalFilter> jurisdictions = country.filterCodes().stream()
                .map(code -> RetrievalFilter.fromListContains(attribute("jurisdictions", code)))
                .toList();
        return RetrievalFilter.fromAndAll(List.of(
                RetrievalFilter.fromEqualsValue(attribute("scope", "avsec")),
                RetrievalFilter.fromOrAll(jurisdictions)));
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
                                                .modelArn(COHERE_MODEL_ARN)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .results().stream()
                .map(result -> pool.get(result.index()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    // ---- 새 프롬프트: 기내·위탁 분리, 가방 정보 없음 (PromptSchemaComparisonManualIT과 동일) ----

    private ConverseResponse converseNew(String regulations, String item) {
        String userPrompt = """
                [규정 발췌문]
                %s

                [물품 목록] (총 1줄 — 모두 판정할 것)
                - itemId=1 | 물품=%s
                """.formatted(regulations, item);
        return bedrockRuntimeClient.converse(ConverseRequest.builder()
                .modelId(properties.modelId())
                .system(SystemContentBlock.fromText(PromptSchemaComparisonManualIT.NEW_SYSTEM_PROMPT))
                .messages(Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText(userPrompt)).build())
                .toolConfig(newToolConfig())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(properties.maxTokens())
                        .temperature(0.0f)
                        .build())
                .build());
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

    private void charge(ConverseResponse response) {
        spentWon += (response.usage().inputTokens() * INPUT_USD + response.usage().outputTokens() * OUTPUT_USD)
                * WON_PER_USD;
    }

    private void guardBudget() {
        if (spentWon > BUDGET_WON) {
            throw new IllegalStateException("추정 비용이 예산 %.0f원을 넘어 멈춘다".formatted(BUDGET_WON));
        }
    }

    private static String label(String level) {
        return switch (level) {
            case "PROHIBITED" -> "금지";
            case "CONDITIONAL" -> "주의";
            case "ALLOWED" -> "통과";
            case "NO_EVIDENCE" -> "통과(근거없음)";
            default -> level;
        };
    }

    private static String oneLine(String text) {
        return text == null || text.isBlank() ? "(비움)" : text.replace("\n", " ").replace("|", "/");
    }

    private void write(StringBuilder report, Map<String, Map<CountryJurisdiction, String>> matrix) throws IOException {
        Files.createDirectories(REPORT.getParent());
        StringBuilder out = new StringBuilder(report);
        out.append("\n## 표 (기내백 가정, · = 통과)\n\n")
                .append("| 물품 | ")
                .append(COUNTRIES.stream().map(Enum::name).collect(Collectors.joining(" | ")))
                .append(" |\n")
                .append("| --- |").append(" --- |".repeat(COUNTRIES.size())).append("\n");
        matrix.forEach((item, byCountry) -> {
            out.append("| ").append(item).append(" | ");
            out.append(COUNTRIES.stream()
                    .map(country -> {
                        String value = byCountry.get(country);
                        return value == null ? "" : ("통과".equals(value) ? "·" : value);
                    })
                    .collect(Collectors.joining(" | ")));
            out.append(" |\n");
        });
        Files.writeString(REPORT, out.toString());
    }

    /** 새 형식 한 건. 등급·문구는 가방 구분으로 코드가 정한다(PromptSchemaComparisonManualIT.NewVerdict와 동일 로직). */
    private record NewVerdict(String cabin, String checked, String cabinGuide, String checkedGuide) {

        private static final List<String> STRICTNESS = List.of("ALLOWED", "NO_EVIDENCE", "CONDITIONAL", "PROHIBITED");

        String levelFor(String carriage) {
            return switch (carriage) {
                case "기내" -> cabin;
                case "위탁" -> checked;
                default -> STRICTNESS.indexOf(cabin) >= STRICTNESS.indexOf(checked) ? cabin : checked;
            };
        }
    }
}
