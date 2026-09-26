package ssafy.checkmate.domain.checklist.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.FilterAttribute;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.global.config.BedrockProperties;

/**
 * 검색어 언어에 따라 규정 검색이 얼마나 달라지는지 실제 호출로 재본다(30번).
 *
 * 세 가지를 같은 물품·같은 필터로 비교한다.
 * ① 한국어만 ② 한국어 + 현지어 ③ 현지어만
 *
 * 현지어는 {@link ItemTranslationClient}가 실제로 GMS에서 받아온 값을 쓴다 — 손으로 적은
 * 별칭이 아니라 운영에서 나갈 값 그대로여야 비교가 의미 있다.
 *
 * 판정(Converse)까지 비교하는 건 ①②뿐이다. ③은 프롬프트의 물품 목록이 한국어라 운영에서
 * 만들 수 없는 조합이고, 검색 단계 비교만으로 충분하다.
 *
 * <b>실제로 과금된다.</b> 실수로 도는 걸 막으려고 환경변수로 잠가 둔다.
 * 실행: {@code RAG_COMPARE=true ./gradlew test --rerun
 *        --tests "ssafy.checkmate.domain.checklist.service.SearchLanguageComparisonManualIT"}
 * 결과는 {@code build/rag-language-comparison-<나라들>.md}에 쌓인다.
 */
@Disabled("실호출·과금 테스트 — 자동으로 돌지 않게 막아 둔다. 돌려야 하면 이 줄을 지우고 로컬에서만 실행")
@SpringBootTest
class SearchLanguageComparisonManualIT {

    /** 마스터 23종과 그 밖의 물품을 섞는다 — 마스터 밖 물품이 이번 변경의 핵심이다. */
    private static final List<String> ITEMS = List.of("맥가이버칼", "보조배터리", "치약", "손톱깎이");

    /**
     * 기본은 번역이 필요한 나라 전부. 한 번에 다 돌리면 오래 걸려서
     * {@code RAG_COMPARE_COUNTRIES=TW,PH}처럼 나눠 돌릴 수 있게 열어 둔다.
     * 한국({@code KR})은 규정 문서가 한국어라 비교할 게 없어 기본 목록에서 빠진다.
     */
    private static final List<CountryJurisdiction> COUNTRIES = countries();

    private static List<CountryJurisdiction> countries() {
        String selected = System.getenv("RAG_COMPARE_COUNTRIES");
        if (selected == null || selected.isBlank()) {
            return Arrays.stream(CountryJurisdiction.values())
                    .filter(CountryJurisdiction::needsTranslation)
                    .toList();
        }
        return Arrays.stream(selected.split(","))
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .map(CountryJurisdiction::valueOf)
                .toList();
    }

    /** 운영의 {@code BedrockAviationRegulationClient}가 질의에 붙이는 꼬리말과 같아야 한다. */
    private static final String QUERY_SUFFIX = " 기내 반입 위탁 수하물 제한";
    private static final String SCOPE_ATTRIBUTE = "scope";
    private static final String SCOPE_VALUE = "avsec";
    private static final String JURISDICTIONS_ATTRIBUTE = "jurisdictions";
    private static final String NO_TRANSLATION = "(번역 없음)";

    /** 나눠 돌린 결과가 서로 덮어쓰지 않게 파일 이름에 나라를 적는다. */
    private static final Path REPORT = Path.of("build", "rag-language-comparison-%s.md".formatted(
            COUNTRIES.stream().map(Enum::name).collect(Collectors.joining("-"))));

    @Autowired
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @Autowired
    private BedrockProperties properties;

    @Autowired
    private ItemTranslationClient itemTranslationClient;

    @Autowired
    private AviationRegulationClient aviationRegulationClient;

    @Test
    @DisplayName("한국어 / 한국어+현지어 / 현지어 세 검색어의 검색 품질과 판정을 비교한다")
    void compareSearchLanguages() throws IOException {
        StringBuilder report = new StringBuilder("# 검색어 언어 비교 (실호출)\n\n")
                .append("청크수=").append(properties.numberOfResults())
                .append(" / 판정모델=").append(properties.modelId()).append("\n");

        COUNTRIES.forEach(country -> {
            Map<String, String> localTerms = itemTranslationClient.translate(ITEMS, country);
            report.append(retrievalTable(country, localTerms));
            report.append(verdictTable(country, localTerms));
        });

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString());
        System.out.println(report);
    }

    /** 세 검색어의 검색 결과를 물품별로 한 줄씩 적는다. */
    private String retrievalTable(CountryJurisdiction country, Map<String, String> localTerms) {
        StringBuilder table = new StringBuilder("\n## %s (%s)\n\n".formatted(country, country.getLanguageName()))
                .append("| 물품 | 받은 현지어 | 검색어 | 청크 | 최고점수 | 현지어 등장 청크 | 첫 등장 순위 |\n")
                .append("|---|---|---|---|---|---|---|\n");

        ITEMS.forEach(item -> {
            String terms = localTerms.getOrDefault(item, "");
            Map<String, String> queries = new LinkedHashMap<>();
            queries.put("한국어", item);
            queries.put("한국어+현지어", terms.isBlank() ? NO_TRANSLATION : item + " " + terms);
            queries.put("현지어", terms.isBlank() ? NO_TRANSLATION : terms);

            queries.forEach((label, query) -> {
                if (NO_TRANSLATION.equals(query)) {
                    table.append("| %s | 번역 실패 | %s | - | - | - | - |\n".formatted(item, label));
                    return;
                }
                List<Hit> hits = retrieve(query, country);
                table.append("| %s | %s | %s | %d | %s | %d | %s |\n".formatted(
                        item, terms.isBlank() ? "-" : terms, label, hits.size(), topScore(hits),
                        hitCount(hits, terms), firstHitRank(hits, terms)));
            });
        });
        return table.toString();
    }

    /**
     * 같은 물품 묶음을 한국어만 / 한국어+현지어로 각각 판정받아 등급이 갈리는지 본다.
     * 검색이 규정을 놓치면 근거가 없어 통과(NONE)로 떨어지므로, 등급 차이가 곧 검색 차이다.
     */
    private String verdictTable(CountryJurisdiction country, Map<String, String> localTerms) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (int index = 0; index < ITEMS.size(); index++) {
            names.put((long) index + 1, ITEMS.get(index));
        }

        Map<Long, SecurityVerdict> koreanOnly = judge(country, names, item -> "");
        Map<Long, SecurityVerdict> withLocal = judge(country, names, item -> localTerms.getOrDefault(item, ""));

        StringBuilder table = new StringBuilder("\n### %s 최종 판정\n\n".formatted(country))
                .append("| 물품 | 한국어만 | 한국어+현지어 | 안내 문구(한국어+현지어) |\n")
                .append("|---|---|---|---|\n");
        names.forEach((itemId, name) -> table.append("| %s | %s | %s | %s |\n".formatted(
                name, levelOf(koreanOnly, itemId), levelOf(withLocal, itemId), guideOf(withLocal, itemId))));
        return table.toString();
    }

    private Map<Long, SecurityVerdict> judge(CountryJurisdiction country, Map<Long, String> names,
            Function<String, String> localTermsOf) {
        List<SecurityCheckTarget> targets = names.entrySet().stream()
                .map(entry -> new SecurityCheckTarget(entry.getKey(), entry.getValue(), "기내백", "기내",
                        localTermsOf.apply(entry.getValue())))
                .toList();
        return aviationRegulationClient.judge(country, targets).stream()
                .collect(Collectors.toMap(SecurityVerdict::getItemId, verdict -> verdict, (first, next) -> first));
    }

    private List<Hit> retrieve(String query, CountryJurisdiction country) {
        RetrieveResponse response = bedrockAgentRuntimeClient.retrieve(RetrieveRequest.builder()
                .knowledgeBaseId(properties.knowledgeBaseId())
                .retrievalQuery(KnowledgeBaseQuery.builder().text(query + QUERY_SUFFIX).build())
                .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                        .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                .numberOfResults(properties.numberOfResults())
                                .filter(filterFor(country))
                                .build())
                        .build())
                .build());

        List<Hit> hits = new ArrayList<>();
        for (KnowledgeBaseRetrievalResult result : response.retrievalResults()) {
            hits.add(new Hit(result.content().text(), result.score()));
        }
        return hits;
    }

    /** 운영과 같은 필터 — 해당 국가 문서와 공통(GLOBAL) 문서를 함께 본다. */
    private RetrievalFilter filterFor(CountryJurisdiction country) {
        List<RetrievalFilter> jurisdictions = country.filterCodes().stream()
                .map(code -> RetrievalFilter.fromListContains(FilterAttribute.builder()
                        .key(JURISDICTIONS_ATTRIBUTE).value(Document.fromString(code)).build()))
                .toList();
        return RetrievalFilter.fromAndAll(List.of(
                RetrievalFilter.fromEqualsValue(FilterAttribute.builder()
                        .key(SCOPE_ATTRIBUTE).value(Document.fromString(SCOPE_VALUE)).build()),
                RetrievalFilter.fromOrAll(jurisdictions)));
    }

    /**
     * 검색해 온 규정 본문에 그 물품의 현지어가 실제로 적혀 있는지 센다.
     * 정답 청크를 손으로 달아 두지 않아도 "이 규정이 그 물품을 다루는가"의 대리 지표가 된다.
     */
    private static long hitCount(List<Hit> hits, String terms) {
        return hits.stream().filter(hit -> hit.mentionsAny(terms)).count();
    }

    private static String firstHitRank(List<Hit> hits, String terms) {
        for (int index = 0; index < hits.size(); index++) {
            if (hits.get(index).mentionsAny(terms)) {
                return String.valueOf(index + 1);
            }
        }
        return "없음";
    }

    private static String topScore(List<Hit> hits) {
        return hits.stream()
                .map(Hit::score)
                .filter(score -> score != null)
                .max(Double::compareTo)
                .map("%.4f"::formatted)
                .orElse("-");
    }

    private static String levelOf(Map<Long, SecurityVerdict> verdicts, Long itemId) {
        SecurityVerdict verdict = verdicts.get(itemId);
        return verdict == null ? "판정없음" : verdict.getLevel().getLabel();
    }

    private static String guideOf(Map<Long, SecurityVerdict> verdicts, Long itemId) {
        SecurityVerdict verdict = verdicts.get(itemId);
        return verdict == null || verdict.getGuide() == null ? "-" : verdict.getGuide().replace("\n", " ");
    }

    private record Hit(String text, Double score) {

        private boolean mentionsAny(String terms) {
            if (terms.isBlank()) {
                return false;
            }
            String haystack = text.toLowerCase();
            for (String term : terms.split("\\s+")) {
                if (!term.isBlank() && haystack.contains(term.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
    }
}
