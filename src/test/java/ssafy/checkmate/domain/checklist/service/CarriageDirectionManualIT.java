package ssafy.checkmate.domain.checklist.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.global.config.BedrockProperties;

/**
 * 보조배터리 안내 문구의 <b>방향</b>이 맞는지 실호출로 재본다(30번).
 *
 * 보조배터리는 반입 규정이 칼과 정반대다 — <b>기내만 되고 위탁이 금지</b>다. 그런데 운영에서
 * 한국행 검사가 "위탁 수하물로 옮기라"는 뒤집힌 안내를 냈다. 칼처럼 "기내 불가 → 위탁으로"
 * 방향으로 문구가 나오면 사용자가 지시대로 따랐을 때 오히려 규정을 어기게 된다.
 *
 * 그래서 세 축으로 나눠 본다.
 * ① 나라 — 한국만 그러는지 ② 가방 구분 — 미지정일 때만 그러는지 ③ 모델 — Haiku/Sonnet 차이인지
 *
 * LLM은 {@code temperature=0}이어도 완전한 결정성이 없어 조합마다 3회씩 돌린다
 * (모델선정 리포트 §4-5와 같은 방법).
 *
 * <b>실제로 과금된다.</b> 실행:
 * {@code RAG_COMPARE=true ./gradlew test --rerun
 *        --tests "ssafy.checkmate.domain.checklist.service.CarriageDirectionManualIT"}
 * 결과는 {@code build/rag-carriage-direction.md}에 쌓인다.
 */
@Disabled("실호출·과금 테스트 — 자동으로 돌지 않게 막아 둔다. 돌려야 하면 이 줄을 지우고 로컬에서만 실행")
@SpringBootTest
class CarriageDirectionManualIT {

    private static final String ITEM = "보조배터리";
    private static final int REPEATS = 3;

    /** 운영에서 뒤집힌 한국, 앞선 비교에서 통과로 빠진 미국, 정상이던 일본(대조군). */
    private static final List<CountryJurisdiction> COUNTRIES =
            List.of(CountryJurisdiction.KR, CountryJurisdiction.US, CountryJurisdiction.JP);

    /** 가방이 생성 시 미지정으로 만들어지므로 미지정도 실제로 흔한 입력이다. */
    private static final List<Bag> BAGS = List.of(
            new Bag("기내백", "기내"),
            new Bag("캐리어", "위탁"),
            new Bag("내 가방", SecurityCheckTarget.UNSPECIFIED));

    /**
     * 기본은 현재 운영 모델과 모델선정 리포트의 대조군(Sonnet 4.6).
     * Sonnet 5 이상은 {@code temperature}를 받지 않아(보내면 400) 여기 넣으면 전부 실패한다 —
     * 운영 클라이언트에서 샘플링 파라미터를 뺀 뒤에 넣을 것.
     */
    private static final List<String> MODELS = models();

    private static List<String> models() {
        String selected = System.getenv("RAG_COMPARE_MODELS");
        if (selected == null || selected.isBlank()) {
            return List.of("global.anthropic.claude-haiku-4-5-20251001-v1:0",
                    "global.anthropic.claude-sonnet-4-6");
        }
        return Arrays.stream(selected.split(",")).map(String::trim).filter(code -> !code.isBlank()).toList();
    }

    private static final Path REPORT = Path.of("build", "rag-carriage-direction.md");

    @Autowired
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @Autowired
    private BedrockRuntimeClient bedrockRuntimeClient;

    @Autowired
    private BedrockProperties properties;

    @Autowired
    private ItemTranslationClient itemTranslationClient;

    @Test
    @DisplayName("보조배터리 안내 문구가 나라·가방 구분·모델에 따라 뒤집히는지 본다")
    void comparesCarriageGuideDirection() throws IOException {
        StringBuilder report = new StringBuilder("# 보조배터리 안내 방향 (실호출)\n\n")
                .append("물품=").append(ITEM)
                .append(" / 반복=").append(REPEATS).append("회")
                .append(" / 청크수=").append(properties.numberOfResults()).append("\n");

        for (String modelId : MODELS) {
            report.append("\n## ").append(modelId).append("\n\n")
                    .append("| 국가 | 가방 | 구분 | 회차 | 판정 | 안내 문구 |\n")
                    .append("|---|---|---|---|---|---|\n");
            AviationRegulationClient client = clientFor(modelId);

            for (CountryJurisdiction country : COUNTRIES) {
                String localTerms = localTermsOf(country);
                for (Bag bag : BAGS) {
                    for (int round = 1; round <= REPEATS; round++) {
                        report.append(row(client, country, bag, localTerms, round));
                    }
                }
            }
        }

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString());
        System.out.println(report);
    }

    private String row(AviationRegulationClient client, CountryJurisdiction country, Bag bag,
            String localTerms, int round) {
        SecurityCheckTarget target =
                new SecurityCheckTarget(1L, ITEM, bag.name(), bag.carriageType(), localTerms);
        List<SecurityVerdict> verdicts = client.judge(country, List.of(target));

        if (verdicts.isEmpty()) {
            return "| %s | %s | %s | %d | 판정없음 | - |\n"
                    .formatted(country, bag.name(), bag.carriageType(), round);
        }
        SecurityVerdict verdict = verdicts.get(0);
        String guide = verdict.getGuide() == null ? "-" : verdict.getGuide().replace("\n", " ");
        return "| %s | %s | %s | %d | %s | %s |\n".formatted(
                country, bag.name(), bag.carriageType(), round, verdict.getLevel().getLabel(), guide);
    }

    /** 운영과 같은 경로로 현지어를 받는다. 한국행은 번역을 타지 않아 빈 문자열이다. */
    private String localTermsOf(CountryJurisdiction country) {
        if (!country.needsTranslation()) {
            return "";
        }
        Map<String, String> translations = itemTranslationClient.translate(List.of(ITEM), country);
        return translations.getOrDefault(ITEM, "");
    }

    /** 모델만 바꾼 운영 클라이언트 — 검색·프롬프트·스키마는 전부 운영과 같다. */
    private AviationRegulationClient clientFor(String modelId) {
        BedrockProperties overridden = new BedrockProperties(properties.region(),
                properties.knowledgeBaseId(), modelId, properties.numberOfResults(), properties.maxTokens());
        return new BedrockAviationRegulationClient(
                bedrockAgentRuntimeClient, bedrockRuntimeClient, overridden);
    }

    private record Bag(String name, String carriageType) {
    }
}
