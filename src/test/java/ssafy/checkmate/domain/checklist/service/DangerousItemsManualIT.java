package ssafy.checkmate.domain.checklist.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * 코퍼스가 "명백히 금지된 물품"(폭발물, 총기류)을 실제로 얼마나 잘 잡는지 확인하는 스팟체크.
 *
 * 23종 마스터 물품에는 없는 극단적인 사례(폭탄, M-16 소총)를 새 출력 형식(기내·위탁 분리,
 * {@link PromptSchemaComparisonManualIT#NEW_SYSTEM_PROMPT})으로 10개국에 물어본다. 이런
 * 물품은 국가 규정이 아니라 GLOBAL(IATA/ICAO 공통) 문서에 있을 가능성이 높아, 검색 필터가
 * GLOBAL을 제대로 물고 오는지도 같이 확인된다.
 *
 * <b>실제로 과금된다(약 400~500원 예상, 20쌍).</b> 실행:
 * {@code DANGEROUS_ITEMS=true ./gradlew test --rerun
 *        --tests "ssafy.checkmate.domain.checklist.service.DangerousItemsManualIT"}
 * 결과는 {@code build/dangerous-items.md}에 남는다.
 */
@Disabled("실호출·과금 테스트 — 자동으로 돌지 않게 막아 둔다. 돌려야 하면 이 줄을 지우고 로컬에서만 실행")
@SpringBootTest
class DangerousItemsManualIT {

    private static final List<CountryJurisdiction> COUNTRIES = List.of(
            CountryJurisdiction.JP, CountryJurisdiction.CN, CountryJurisdiction.TW, CountryJurisdiction.TH,
            CountryJurisdiction.VN, CountryJurisdiction.PH, CountryJurisdiction.US, CountryJurisdiction.GB,
            CountryJurisdiction.FR, CountryJurisdiction.IT);

    private static final List<String> ITEMS = List.of("폭탄", "M-16 소총");

    private static final String QUERY_SUFFIX = " 기내 반입 위탁 수하물 제한";
    private static final int POOL_SIZE = 30;
    private static final int RERANK_TOP = 10;
    private static final String COHERE_MODEL_ARN = "arn:aws:bedrock:ap-northeast-1::foundation-model/cohere.rerank-v3-5:0";
    private static final String NEW_TOOL_NAME = "report_carriage_verdicts";

    private static final double INPUT_USD = 3.0 / 1_000_000;
    private static final double OUTPUT_USD = 15.0 / 1_000_000;
    private static final double RERANK_USD = 2.0 / 1_000;
    private static final double WON_PER_USD = 1_400;
    private static final double BUDGET_WON = 2_000;

    private static final Path REPORT = Path.of("build", "dangerous-items.md");

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
    @DisplayName("폭탄·M-16을 새 형식(기내·위탁 분리)으로 10개국에 판정받는다")
    void judgeDangerousItems() throws Exception {
        StringBuilder report = new StringBuilder("# 극단 사례 스팟체크 — 폭탄·M-16 (새 형식, 10개국)\n\n")
                .append("판정 모델=").append(properties.modelId())
                .append(" / 검색: 벡터 top-").append(POOL_SIZE).append(" → Cohere top-").append(RERANK_TOP).append("\n\n")
                .append("| 물품 | 국가 | 현지어 검색어 | 기내 | 위탁 | cabinGuide | checkedGuide |\n")
                .append("|---|---|---|---|---|---|---|\n");

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (String item : ITEMS) {
                for (CountryJurisdiction country : COUNTRIES) {
                    guardBudget();
                    String term = itemTranslationClient.translate(List.of(item), country).getOrDefault(item, item);
                    List<String> pool = retrieve(term + QUERY_SUFFIX, country);
                    String regulations = pool.isEmpty() ? "" : rerankTop(tokyo, term, pool);
                    if (!pool.isEmpty()) {
                        spentWon += RERANK_USD * WON_PER_USD;
                    }

                    ConverseResponse response = converseNew(regulations, item);
                    charge(response);
                    NewVerdict verdict = parseNew(response);

                    report.append("| %s | %s | %s | %s | %s | %s | %s |\n".formatted(
                            item, country, term, label(verdict.cabin), label(verdict.checked),
                            oneLine(verdict.cabinGuide), oneLine(verdict.checkedGuide)));
                    write(report);
                }
            }
        }
        report.append("\n총 추정 비용: %.0f원\n".formatted(spentWon));
        write(report);
        System.out.println(report);
    }

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

    private void write(StringBuilder report) throws IOException {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString());
    }

    private record NewVerdict(String cabin, String checked, String cabinGuide, String checkedGuide) {
    }
}
