package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.domain.checklist.service.AviationRegulationClient;
import ssafy.checkmate.domain.checklist.service.ItemTranslationClient;
import ssafy.checkmate.domain.checklist.service.SecurityCheckTarget;
import ssafy.checkmate.domain.checklist.service.SecurityVerdict;

/**
 * RAG_검색품질_모델선정_리포트.md의 "230쌍"(Cohere 리랭크 실측)과 <b>같은 기준</b>으로,
 * top-k=30·리랭크 없음의 비용/판정을 재는 수동 확인용 테스트.
 *
 * 리포트의 230쌍 실측은 <b>(국가, 물품) 한 쌍마다 판정을 따로</b> 호출했다(§"Cohere 리랭크 —
 * 방법과 결과"). 운영 코드({@code SecurityCheckService})는 여행 전체를 한 번에 묶어 묻지만,
 * 그렇게 하면 청크가 물품끼리 겹쳐 중복 제거되고 시스템 프롬프트도 나눠 부담해 비용이 크게
 * 줄어든다(먼저 돌린 배치 버전 실측: 230쌍에 약 1,019원 — {@code build/top30-no-rerank-230.md}).
 * 그 숫자는 "실제 운영 비용"으로는 맞지만, 리포트의 42.2원/쌍(no-rerank)·23.7원/쌍(Cohere)과는
 * 계산 기준이 달라 나란히 비교할 수 없다. 이 클래스는 <b>물품 하나씩 따로</b> 호출해서
 * 리포트와 같은 기준의 숫자를 만든다.
 *
 * top-k는 코드가 아니라 {@code .env}의 {@code BEDROCK_SEARCH_RESULTS}로 정해지므로(현재
 * 30), 이 클래스는 값을 바꾸지 않는다.
 *
 * <p>물품마다 검색(retrieve) + 판정(generate)을 따로 호출한다 — 국가당 23회, 총 230회.
 * 배치 버전(10회)보다 훨씬 오래 걸리고(예상 20~40분) 훨씬 많이 과금된다(예상 약 9,700원,
 * 리포트 42.2원/쌍 × 230). 기본은 @Disabled로 꺼둔다 — 로컬에서 확인할 때만 이 줄을 지운다.
 *
 * <p>결과는 {@code build/top30-no-rerank-per-item-230.md}에 표로 남는다(배치 버전 결과
 * 파일은 덮어쓰지 않는다). 국가별 GMS 번역은 리포트와 같이 한 번에 묶어 부른다 — 번역은
 * 판정과 달리 물품 수만큼 나눠 불러도 비교 기준에 영향이 없고, 나눠 부르면 GMS 호출만
 * 늘어난다.
 *
 * 실행: 아래 @Disabled를 지우고
 * {@code ./gradlew test --tests "*Top30NoRerankFullScaleManualIT"}
 */
@Disabled("실호출·과금 테스트 — 자동으로 돌지 않게 막아 둔다. 돌려야 하면 이 줄을 지우고 로컬에서만 실행")
@SpringBootTest
class Top30NoRerankFullScaleManualIT {

    /** 리포트와 같은 10개국. */
    private static final List<CountryJurisdiction> COUNTRIES = List.of(
            CountryJurisdiction.JP, CountryJurisdiction.CN, CountryJurisdiction.TW, CountryJurisdiction.TH,
            CountryJurisdiction.VN, CountryJurisdiction.PH, CountryJurisdiction.US, CountryJurisdiction.GB,
            CountryJurisdiction.FR, CountryJurisdiction.IT);

    /** 리포트와 같은 23종(온디바이스 물품 마스터 기준). */
    private static final List<String> ITEMS = List.of(
            "여권", "지갑", "노트북", "충전기", "이어폰", "상의", "하의", "양말", "신발류", "모자",
            "선글라스", "태블릿", "장갑", "칫솔", "백팩",
            "보조배터리", "우산", "가위", "칼", "삼각대", "상비약", "치약", "텀블러");

    /** 리포트의 230쌍 판정과 같은 가방 가정 — 기내백. */
    private static final String BAG_NAME = "기내백";
    private static final String CARRIAGE_TYPE = "기내";

    private static final Path OUT_FILE = Path.of("build", "top30-no-rerank-per-item-230.md");

    @Autowired
    private ItemTranslationClient itemTranslationClient;
    @Autowired
    private AviationRegulationClient aviationRegulationClient;

    @Test
    @DisplayName("top-k=30·리랭크 없음, 물품별 개별 호출로 10개국×23종(230쌍) 판정을 받아 표로 남긴다")
    void judgesEachPairSeparately() throws IOException {
        Map<String, Map<CountryJurisdiction, String>> matrix = new LinkedHashMap<>();
        ITEMS.forEach(item -> matrix.put(item, new LinkedHashMap<>()));

        for (CountryJurisdiction country : COUNTRIES) {
            judgeCountryPerItem(country, matrix);
        }

        Files.createDirectories(OUT_FILE.getParent());
        Files.writeString(OUT_FILE, renderTable(matrix));
        System.out.println("[top30-per-item] 결과 저장 -> " + OUT_FILE.toAbsolutePath());

        assertThat(matrix).hasSize(ITEMS.size());
    }

    /** 번역만 국가당 한 번에 묶어 부르고, 판정은 물품마다 따로 부른다(리포트와 같은 기준). */
    private void judgeCountryPerItem(CountryJurisdiction country, Map<String, Map<CountryJurisdiction, String>> matrix) {
        Map<String, String> localTerms = country.needsTranslation()
                ? itemTranslationClient.translate(ITEMS, country)
                : Map.of();

        long countryStartedAt = System.nanoTime();
        for (String name : ITEMS) {
            String searchTerm = country.needsTranslation() ? localTerms.getOrDefault(name, "") : name;
            SecurityCheckTarget target = new SecurityCheckTarget(1L, name, BAG_NAME, CARRIAGE_TYPE, searchTerm);

            long startedAt = System.nanoTime();
            List<SecurityVerdict> verdicts = aviationRegulationClient.judge(country, List.of(target));
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            String label = verdicts.stream()
                    .filter(v -> v.getItemId().equals(1L))
                    .findFirst()
                    .map(v -> v.getLevel().getLabel())
                    .orElse("통과");
            matrix.get(name).put(country, label);

            System.out.printf("[top30-per-item] %s-%s 완료 (%dms) -> %s%n", country, name, elapsedMs, label);
        }
        long countryElapsedMs = (System.nanoTime() - countryStartedAt) / 1_000_000;
        System.out.printf("[top30-per-item] %s 23종 전체 완료 (%dms)%n", country, countryElapsedMs);
    }

    /** 리포트의 "230쌍 판정 결과" 표와 같은 모양 — 통과는 '·'로 줄인다. */
    private String renderTable(Map<String, Map<CountryJurisdiction, String>> matrix) {
        StringBuilder sb = new StringBuilder();
        sb.append("# top-k=30 · 리랭크 없음 · 물품별 개별 호출 — 10개국×23종(230쌍)\n\n");
        sb.append("| 물품 | ")
                .append(COUNTRIES.stream().map(Enum::name).collect(Collectors.joining(" | ")))
                .append(" |\n");
        sb.append("| --- |").append(" --- |".repeat(COUNTRIES.size())).append("\n");

        matrix.forEach((item, byCountry) -> {
            sb.append("| ").append(item).append(" | ");
            sb.append(COUNTRIES.stream()
                    .map(country -> {
                        String label = byCountry.get(country);
                        return "통과".equals(label) ? "·" : label;
                    })
                    .collect(Collectors.joining(" | ")));
            sb.append(" |\n");
        });
        return sb.toString();
    }
}
