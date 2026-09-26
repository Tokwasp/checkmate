package ssafy.checkmate.domain.checklist.service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.global.config.BedrockProperties;

/**
 * 벡터 top-30 → Cohere Rerank 3.5(현지어 이름 질의) → top-10 으로 판정(Converse)까지 전부 돌린다(30번).
 *
 * RAG_검색품질_모델선정_리포트.md 발견 1의 230쌍·18쌍 측정 — (국가, 물품) 한 쌍마다 판정을 한 번 받는다.
 * 판정 요청은 운영 {@link BedrockAviationRegulationClient}의 시스템 프롬프트·유저 프롬프트·도구 스키마를
 * 그대로 쓴다(도구 설정·파싱은 private이라 리플렉션으로 부른다 — 운영 코드는 건드리지 않는다).
 *
 * <b>실제로 과금된다(약 5,200원 예상).</b> 누적 비용이 {@link #BUDGET_WON}을 넘으면 즉시 멈춘다.
 * 실행: {@code RERANK_VERDICT=true ./gradlew test --rerun
 *        --tests "ssafy.checkmate.domain.checklist.service.CohereRerankVerdictManualIT"}
 * 결과는 나라 하나가 끝날 때마다 {@code build/cohere-rerank-verdict.md}에 덮어써 남는다.
 */
@Disabled("실호출·과금 테스트 — 자동으로 돌지 않게 막아 둔다. 돌려야 하면 이 줄을 지우고 로컬에서만 실행")
@SpringBootTest
class CohereRerankVerdictManualIT {

    /** AI/classes.txt 의 마스터 23종. */
    private static final List<String> ITEMS = List.of(
            "여권", "지갑", "노트북", "충전기", "보조배터리", "이어폰", "상의", "하의", "양말", "신발류", "모자",
            "선글라스", "우산", "가위", "칼", "태블릿", "삼각대", "상비약", "장갑", "칫솔", "치약", "텀블러", "백팩");

    private static final String QUERY_SUFFIX = " 기내 반입 위탁 수하물 제한";
    private static final int POOL_SIZE = 30;
    private static final int RERANK_TOP = 10;
    private static final String COHERE_MODEL_ARN = "arn:aws:bedrock:ap-northeast-1::foundation-model/cohere.rerank-v3-5:0";
    private static final String BAG_NAME = "기내백";
    private static final String CARRIAGE = "기내";

    /** Sonnet 4.6 단가(USD/토큰)와 Cohere 쿼리 단가, 환율. 비용은 이 값으로 추정한다. */
    private static final double INPUT_USD = 3.0 / 1_000_000;
    private static final double OUTPUT_USD = 15.0 / 1_000_000;
    private static final double RERANK_USD = 2.0 / 1_000;
    private static final double WON_PER_USD = 1_400;
    private static final double BUDGET_WON = 8_000;

    private static final Path REPORT = Path.of("build", "cohere-rerank-verdict.md");

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
    @DisplayName("Cohere 리랭크 top-10 으로 10개국 × 23종 판정을 받는다")
    void judgeAllPairs() throws Exception {
        StringBuilder report = new StringBuilder("# Cohere Rerank 3.5 (A: 이름) top-10 판정 — 10개국 × 23종\n\n")
                .append("판정 모델=").append(properties.modelId())
                .append(" / 가방=").append(BAG_NAME).append("(").append(CARRIAGE).append(")")
                .append(" / 벡터 top-").append(POOL_SIZE).append(" → Cohere top-").append(RERANK_TOP).append("\n");

        List<CountryJurisdiction> countries = Arrays.stream(CountryJurisdiction.values())
                .filter(CountryJurisdiction::needsTranslation)
                .toList();

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (CountryJurisdiction country : countries) {
                report.append(judgeCountry(tokyo, country));
                report.append("\n누적 추정 비용: %.0f원\n".formatted(spentWon));
                write(report);
            }
        }
        report.append("\n## 합계\n\n총 추정 비용: %.0f원\n".formatted(spentWon));
        write(report);
        System.out.println(report);
    }

    /**
     * 230쌍에서 의심스러운 판정이 나온 쌍만 기존(벡터 top-15) vs Cohere top-10 으로 나란히 판정하고,
     * 넘긴 규정 본문까지 남긴다 — rerank가 규정을 빠뜨린 건지, 원래 판정이 그런 건지 가른다.
     * 실행: {@code RERANK_DIAG=true ./gradlew test --rerun --tests "...CohereRerankVerdictManualIT.diagnose"}
     */
    @Test
    @DisplayName("의심 판정 쌍을 기존 방식과 Cohere 방식으로 나란히 판정한다")
    void diagnose() throws Exception {
        Map<CountryJurisdiction, List<String>> cases = new java.util.LinkedHashMap<>();
        cases.put(CountryJurisdiction.TH, List.of("보조배터리"));
        cases.put(CountryJurisdiction.PH, List.of("보조배터리"));
        cases.put(CountryJurisdiction.FR, List.of("가위"));
        cases.put(CountryJurisdiction.JP, List.of("텀블러"));
        cases.put(CountryJurisdiction.IT, List.of("상비약"));

        StringBuilder summary = new StringBuilder("# 의심 판정 진단 — 기존(벡터 top-15) vs Cohere top-10\n\n")
                .append("| 국가 | 물품 | 기존 판정 | 기존 안내 | Cohere 판정 | Cohere 안내 |\n|---|---|---|---|---|---|\n");
        StringBuilder details = new StringBuilder("\n# 넘긴 규정 본문\n");

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (Map.Entry<CountryJurisdiction, List<String>> entry : cases.entrySet()) {
                CountryJurisdiction country = entry.getKey();
                Map<String, String> terms = itemTranslationClient.translate(entry.getValue(), country);
                for (String item : entry.getValue()) {
                    String term = terms.getOrDefault(item, "");
                    List<String> pool = retrieve(term + QUERY_SUFFIX, country);
                    String baselineRegs = String.join("\n\n---\n\n", pool.subList(0, Math.min(15, pool.size())));
                    String cohereRegs = rerankTop(tokyo, term, pool);

                    SecurityCheckTarget target = new SecurityCheckTarget(1L, item, BAG_NAME, CARRIAGE, term);
                    SecurityVerdict baseline = firstVerdict(converse(baselineRegs, List.of(target)));
                    SecurityVerdict cohere = firstVerdict(converse(cohereRegs, List.of(target)));

                    summary.append("| %s | %s | %s | %s | %s | %s |\n".formatted(country, item,
                            levelOf(baseline), guideOf(baseline), levelOf(cohere), guideOf(cohere)));
                    details.append("\n## %s — %s (검색어: %s)\n\n### 기존 top-15\n\n%s\n\n### Cohere top-10\n\n%s\n"
                            .formatted(country, item, term, baselineRegs, cohereRegs));
                }
            }
        }
        Path out = Path.of("build", "cohere-rerank-diagnosis.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, summary.append(details).toString());
        System.out.println(summary);
    }

    /**
     * 리랭크 없이 벡터 top-30을 그대로 넘긴 판정 vs 같은 30개를 Cohere top-10으로 줄인 판정 — 18쌍, 1회.
     * 실행: {@code RERANK_VERDICT=true RERANK_TOP30=true ./gradlew test --rerun --tests "...CohereRerankVerdictManualIT.top30VsCohere"}
     */
    @Test
    @DisplayName("리랭크 없음(top-30 그대로) vs Cohere top-10 — 같은 검색 결과로 18쌍 판정")
    void top30VsCohere() throws Exception {
        Map<CountryJurisdiction, List<String>> cases = new java.util.LinkedHashMap<>();
        cases.put(CountryJurisdiction.TW, List.of("칼", "상비약", "치약", "텀블러"));
        cases.put(CountryJurisdiction.FR, List.of("칼", "가위", "상비약", "텀블러"));
        cases.put(CountryJurisdiction.VN, List.of("칼", "보조배터리", "우산"));
        cases.put(CountryJurisdiction.TH, List.of("칼", "보조배터리"));
        cases.put(CountryJurisdiction.IT, List.of("노트북", "상비약"));
        cases.put(CountryJurisdiction.JP, List.of("텀블러"));
        cases.put(CountryJurisdiction.GB, List.of("가위"));
        cases.put(CountryJurisdiction.PH, List.of("보조배터리"));

        StringBuilder table = new StringBuilder("# 리랭크 없음(top-30) vs Cohere top-10 — 18쌍, 같은 검색 결과\n\n")
                .append("| 국가-물품 | top-30 판정 | top-30 입력토큰 | top-30 비용 | Cohere 판정 | Cohere 입력토큰 | Cohere 비용(리랭크 포함) | top-30 안내 | Cohere 안내 |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        double top30Won = 0;
        double cohereWon = 0;

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (Map.Entry<CountryJurisdiction, List<String>> entry : cases.entrySet()) {
                CountryJurisdiction country = entry.getKey();
                Map<String, String> terms = itemTranslationClient.translate(entry.getValue(), country);
                for (String item : entry.getValue()) {
                    if (spentWon > 3_000) {
                        throw new IllegalStateException("추정 비용이 3,000원을 넘어 멈춘다");
                    }
                    String term = terms.getOrDefault(item, "");
                    List<String> pool = retrieve(term + QUERY_SUFFIX, country);
                    String allRegs = String.join("\n\n---\n\n", pool);
                    String cohereRegs = rerankTop(tokyo, term, pool);
                    SecurityCheckTarget target = new SecurityCheckTarget(1L, item, BAG_NAME, CARRIAGE, term);

                    ConverseResponse all = converse(allRegs, List.of(target));
                    ConverseResponse cohere = converse(cohereRegs, List.of(target));
                    double allCost = costWon(all);
                    double cohereCost = costWon(cohere) + RERANK_USD * WON_PER_USD;
                    top30Won += allCost;
                    cohereWon += cohereCost;
                    spentWon += allCost + cohereCost;

                    SecurityVerdict allVerdict = firstVerdict(all);
                    SecurityVerdict cohereVerdict = firstVerdict(cohere);
                    table.append("| %s-%s | %s | %d | %.1f | %s | %d | %.1f | %s | %s |\n".formatted(
                            country, item, levelOf(allVerdict), all.usage().inputTokens(), allCost,
                            levelOf(cohereVerdict), cohere.usage().inputTokens(), cohereCost,
                            guideOf(allVerdict), guideOf(cohereVerdict)));
                }
            }
        }
        table.append("\n합계: top-30 %.0f원 / Cohere %.0f원 (%.1f%%)\n"
                .formatted(top30Won, cohereWon, (cohereWon / top30Won - 1) * 100));
        Path out = Path.of("build", "top30-vs-cohere.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, table.toString());
        System.out.println(table);
    }

    private double costWon(ConverseResponse response) {
        return (response.usage().inputTokens() * INPUT_USD + response.usage().outputTokens() * OUTPUT_USD)
                * WON_PER_USD;
    }

    private SecurityVerdict firstVerdict(ConverseResponse response) throws Exception {
        List<SecurityVerdict> verdicts = parseVerdicts(response);
        return verdicts.isEmpty() ? null : verdicts.get(0);
    }

    private static String levelOf(SecurityVerdict verdict) {
        return verdict == null ? "판정없음" : verdict.getLevel().name();
    }

    private static String guideOf(SecurityVerdict verdict) {
        return verdict == null || verdict.getGuide() == null
                ? "-" : verdict.getGuide().replace("\n", " ").replace("|", "/");
    }

    private String judgeCountry(BedrockAgentRuntimeClient tokyo, CountryJurisdiction country) throws Exception {
        Map<String, String> terms = itemTranslationClient.translate(ITEMS, country);
        StringBuilder table = new StringBuilder("\n## %s (%s)\n\n".formatted(country, country.getLanguageName()))
                .append("| 물품 | 현지어 검색어 | 판정 | 안내 문구 | 입력토큰 | 출력토큰 | 비용(원) |\n")
                .append("|---|---|---|---|---|---|---|\n");

        for (int index = 0; index < ITEMS.size(); index++) {
            String item = ITEMS.get(index);
            String term = terms.getOrDefault(item, "");
            if (term.isBlank()) {
                table.append("| %s | 번역 실패 | (검색 건너뜀) | - | - | - | - |\n".formatted(item));
                continue;
            }
            if (spentWon > BUDGET_WON) {
                throw new IllegalStateException("추정 비용이 예산 %.0f원을 넘어 멈춘다".formatted(BUDGET_WON));
            }

            List<String> pool = retrieve(term + QUERY_SUFFIX, country);
            String regulations = pool.isEmpty() ? "" : rerankTop(tokyo, term, pool);
            if (!pool.isEmpty()) {
                spentWon += RERANK_USD * WON_PER_USD;
            }

            SecurityCheckTarget target = new SecurityCheckTarget((long) index + 1, item, BAG_NAME, CARRIAGE, term);
            ConverseResponse response = converse(regulations, List.of(target));
            long inputTokens = response.usage().inputTokens();
            long outputTokens = response.usage().outputTokens();
            double costWon = (inputTokens * INPUT_USD + outputTokens * OUTPUT_USD) * WON_PER_USD;
            spentWon += costWon;

            List<SecurityVerdict> verdicts = parseVerdicts(response);
            String level = verdicts.isEmpty() ? "판정없음" : verdicts.get(0).getLevel().name();
            String guide = verdicts.isEmpty() || verdicts.get(0).getGuide() == null
                    ? "-" : verdicts.get(0).getGuide().replace("\n", " ").replace("|", "/");
            table.append("| %s | %s | %s | %s | %d | %d | %.1f |\n".formatted(
                    item, term, level, guide, inputTokens, outputTokens, costWon));
        }
        return table.toString();
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

    /** 운영과 같은 필터 — scope=avsec 이면서 해당 국가 또는 GLOBAL. */
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

    /** 운영과 같은 모양으로 합친다 — 청크 사이를 "---"로 가른다. */
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

    /** 운영 generateVerdicts와 같은 요청 — 시스템 프롬프트·유저 프롬프트·도구 스키마·temperature 0. */
    private ConverseResponse converse(String regulations, List<SecurityCheckTarget> targets) throws Exception {
        return bedrockRuntimeClient.converse(ConverseRequest.builder()
                .modelId(properties.modelId())
                .system(SystemContentBlock.fromText(BedrockAviationRegulationClient.SYSTEM_PROMPT))
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(aviationClient.userPrompt(regulations, targets)))
                        .build())
                .toolConfig((ToolConfiguration) invokePrivate("verdictToolConfig"))
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(Math.max(properties.maxTokens(), targets.size() * 80))
                        .temperature(0.0f)
                        .build())
                .build());
    }

    @SuppressWarnings("unchecked")
    private List<SecurityVerdict> parseVerdicts(ConverseResponse response) throws Exception {
        return (List<SecurityVerdict>) invokePrivate("parseVerdicts", response);
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

    private void write(StringBuilder report) throws IOException {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString());
    }
}
