package ssafy.checkmate.domain.checklist.service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.SoftAssertions;
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
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankResult;
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
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;
import ssafy.checkmate.global.config.BedrockProperties;

/**
 * 검역(35번 2·3단계)에 <b>현재 방식(벡터 top-{@code properties.numberOfResults()}, 리랭크 없음)</b>과
 * <b>넓혀서 거르는 방식(벡터 top-30 → Cohere Rerank 3.5 → top-5)</b>을 같은 물품으로 나란히 돌려 비교한다.
 *
 * 1단계(GMS Vision)는 건너뛴다 — {@link BedrockQuarantineRegulationManualIT}와 같은 이유로,
 * 사진 대신 {@link ItemAnalysis}를 직접 만들어 넣어 <b>검색·판정만</b> 따로 확인한다.
 *
 * 물품은 실제로 검역에 걸리는 것 위주로 골랐다: 생망고·소시지(신선/가공 식품 — 근거 없으면 보수적으로
 * 판정돼야 하는 분류), 육포(과태료 <b>부과기준 문서</b>가 top-k 밖으로 밀릴 위험이 있는 케이스 —
 * {@link BedrockQuarantineRegulationManualIT#retrievesPenaltyAmounts}가 지키려는 것과 같다), 위스키
 * (면세 한도), 인형(허용 대조군 — 리랭크가 쉬운 케이스까지 망가뜨리진 않는지).
 *
 * <b>실제로 과금된다</b>(검색 4회 + 리랭크 2회 + Haiku 생성 2회 × 물품 수 — 물품 5개 기준 소액).
 * 결과는 {@code build/quarantine-rerank-comparison.md}에 남는다.
 *
 * 실행: 실수로 돌지 않도록 클래스에 {@code @Disabled}를 걸어 두었다. 돌려야 하면 로컬에서 그 줄을 지우고
 * 필요한 메서드만 {@code --tests}로 지정해 실행한다. "현재 방식"은 운영 코드({@code retrieveRegulations})를
 * 그대로 부르므로, 리랭크 없는 기존 방식과 비교하려면 {@code BEDROCK_QUARANTINE_RERANK_ENABLED=false}를 준다.
 */
@Disabled("실호출·과금 테스트 — 자동으로 돌지 않게 막아 둔다. 돌려야 하면 이 줄을 지우고 로컬에서만 실행")
@SpringBootTest
class QuarantineRerankVerdictManualIT {

    private static final List<ItemAnalysis> CASES = List.of(
            new ItemAnalysis("생망고", "신선 과일·채소", List.of("망고"), "생것"),
            new ItemAnalysis("소시지", "육류 가공품", List.of("돼지고기"), "가공 포장"),
            new ItemAnalysis("육포", "육류 가공품", List.of("돼지고기", "소고기"), "건조 가공"),
            new ItemAnalysis("위스키", "주류·담배·향수", List.of("보리", "알코올"), "병입 완제품"),
            new ItemAnalysis("캐릭터 인형", "일반 공산품", List.of("폴리에스터", "솜"), "완제품"));

    /** 과태료 부과기준 문서가 리랭크에서도 살아남는지 이 케이스로 확인한다. */
    private static final String PENALTY_RISK_ITEM = "육포";

    /**
     * 1라운드(CASES)와 겹치지 않게, 한국 검역·통관에 실제로 걸리는 카테고리 위주로 10종을 더 골랐다 —
     * 유제품·수산물·신선식물·종자흙·담배·의약품·야생동식물×2·문화재. 점수 필터링 임계값을 잡으려면
     * "의미 있는 청크가 몇 점부터인지"를 다양한 카테고리에서 봐야 해서, 1라운드보다 카테고리를 넓혔다.
     */
    private static final List<ItemAnalysis> CASES_ROUND2 = List.of(
            new ItemAnalysis("체다치즈", "유제품", List.of("우유"), "냉장 포장"),
            new ItemAnalysis("훈제연어", "수산물·수산가공품", List.of("연어"), "훈제 냉장"),
            new ItemAnalysis("마른멸치", "수산물·수산가공품", List.of("멸치"), "건조"),
            new ItemAnalysis("사과", "신선 과일·채소", List.of("사과"), "생것"),
            new ItemAnalysis("꽃씨앗", "식물·종자·흙", List.of("씨앗"), "포장 씨앗"),
            new ItemAnalysis("담배", "주류·담배·향수", List.of("연초"), "궐련 완제품"),
            new ItemAnalysis("진통제", "의약품·건강기능식품", List.of("아세트아미노펜"), "정제 대량"),
            new ItemAnalysis("상아 조각품", "야생동식물 제품", List.of("상아"), "완제품"),
            new ItemAnalysis("거북등껍질 장신구", "야생동식물 제품", List.of("거북등껍질"), "완제품"),
            new ItemAnalysis("고서화", "문화재·골동품", List.of("종이", "먹"), "골동"));

    /**
     * 실제로 검역·관세에 걸리는(허용이 아닌) 물품 10종 — 1·2라운드에서 이미 실측한 것 중 판정이
     * 금지·제한으로 나온 것들을 그대로 재사용한다. 품목 질의 점수 필터(0.35)가 "진짜 걸리는 물품"에서
     * 근거를 놓치지 않는지 보려는 것이라, 애초에 근거가 없는 인형·치즈 같은 대조군은 뺐다.
     */
    private static final List<ItemAnalysis> ITEMS_FLAGGED = List.of(
            new ItemAnalysis("소시지", "육류 가공품", List.of("돼지고기"), "가공 포장"),
            new ItemAnalysis("육포", "육류 가공품", List.of("돼지고기", "소고기"), "건조 가공"),
            new ItemAnalysis("생망고", "신선 과일·채소", List.of("망고"), "생것"),
            new ItemAnalysis("사과", "신선 과일·채소", List.of("사과"), "생것"),
            new ItemAnalysis("꽃씨앗", "식물·종자·흙", List.of("씨앗"), "포장 씨앗"),
            new ItemAnalysis("담배", "주류·담배·향수", List.of("연초"), "궐련 완제품"),
            new ItemAnalysis("위스키", "주류·담배·향수", List.of("보리", "알코올"), "병입 완제품"),
            new ItemAnalysis("진통제", "의약품·건강기능식품", List.of("아세트아미노펜"), "정제 대량"),
            new ItemAnalysis("상아 조각품", "야생동식물 제품", List.of("상아"), "완제품"),
            new ItemAnalysis("거북등껍질 장신구", "야생동식물 제품", List.of("거북등껍질"), "완제품"));

    /**
     * 품목 질의는 이 점수 밑으로는 "근거 없음"으로 본다 — 지난 실측(15종)에서 진짜 근거가 있으면
     * 0.38 이상, 없으면 아무리 넓혀도 0.40을 못 넘었다. 과태료 질의는 점수로 거르지 않는다 — 코퍼스에
     * 있는 동물검역 벌금표 하나가 물품과 무관해도 항상 0.5대로 뜨는 걸 확인했기 때문이다.
     */
    private static final float ITEM_QUERY_SCORE_THRESHOLD = 0.30f;

    private static final int WIDE_POOL = 30;
    private static final int RERANK_TOP = 5;
    private static final String COHERE_MODEL_ARN =
            "arn:aws:bedrock:ap-northeast-1::foundation-model/cohere.rerank-v3-5:0";

    /**
     * Haiku 4.5 추정 단가(USD/토큰) — 실측 전이라 안전 쪽으로 넉넉히 잡는다. Cohere 단가는
     * {@link CohereRerankVerdictManualIT}와 같은 추정치($2/1,000쿼리)를 쓴다. 실제 청구액은
     * AWS 콘솔에서 대조해야 한다.
     */
    private static final double INPUT_USD = 1.0 / 1_000_000;
    private static final double OUTPUT_USD = 5.0 / 1_000_000;
    private static final double RERANK_USD = 2.0 / 1_000;
    private static final double WON_PER_USD = 1_400;

    /** 이 예산을 넘으면 남은 물품은 건너뛰고 그때까지 모은 결과로 리포트를 남긴다. */
    private static final double BUDGET_WON = 1_000;

    private static final Path REPORT = Path.of("build", "quarantine-rerank-comparison.md");

    /**
     * 골든셋({@code 검역_리랭크_골든셋.md}) — 품목 질의만 top-50 → 리랭크 → 0.3 이상 top-5, 과태료 질의는
     * 운영 그대로(벡터 top-{@code properties.numberOfResults()}, 리랭크 없음). 기대 등급이 null이면
     * 경계 사례라 채점하지 않고 판정만 기록한다.
     */
    private static final int GOLDEN_POOL = 50;
    private static final int GOLDEN_TOP = 5;
    private static final float GOLDEN_SCORE_THRESHOLD = 0.30f;
    /** 0.3을 넘는 품목 청크가 하나도 없을 때, 점수와 상관없이 넣어 보는 비교용 개수. */
    private static final int GOLDEN_FALLBACK_TOP = 3;
    private static final double GOLDEN_BUDGET_WON = 2_500;
    private static final Path GOLDEN_REPORT = Path.of("build", "quarantine-golden-set.md");

    private record GoldenCase(String id, ItemAnalysis item, RegulationLevel expected) {
    }

    private static final List<GoldenCase> GOLDEN_SET = List.of(
            // A. 금지 — 여행자가 맞출 수 있는 조건이 없다
            golden("A1", "육포", "육류 가공품", List.of("소고기", "돼지고기"), "건조 가공", RegulationLevel.PROHIBITED),
            golden("A2", "소시지", "육류 가공품", List.of("돼지고기"), "가공 포장", RegulationLevel.PROHIBITED),
            golden("A3", "하몽", "육류 가공품", List.of("돼지고기"), "건조 숙성", RegulationLevel.PROHIBITED),
            golden("A4", "베이컨", "육류 가공품", List.of("돼지고기"), "훈제 진공 포장", RegulationLevel.PROHIBITED),
            golden("A5", "고기만두", "육류 가공품", List.of("돼지고기", "밀가루", "양배추"), "냉동", RegulationLevel.PROHIBITED),
            golden("A6", "소고기 통조림", "육류 가공품", List.of("소고기"), "멸균 통조림", RegulationLevel.PROHIBITED),
            golden("A7", "체다치즈", "유제품", List.of("우유"), "냉장 포장", RegulationLevel.PROHIBITED),
            golden("A8", "생계란", "육류 가공품", List.of("달걀"), "생것", RegulationLevel.PROHIBITED),
            golden("A9", "생망고", "신선 과일·채소", List.of("망고"), "생것", RegulationLevel.PROHIBITED),
            golden("A10", "사과", "신선 과일·채소", List.of("사과"), "생것", RegulationLevel.PROHIBITED),
            golden("A11", "화분 식물", "식물·종자·흙", List.of("식물", "흙"), "흙에 심긴 생식물", RegulationLevel.PROHIBITED),
            golden("A12", "꽃씨앗", "식물·종자·흙", List.of("씨앗"), "포장 씨앗", RegulationLevel.PROHIBITED),
            golden("A13", "상아 조각품", "야생동식물 제품", List.of("상아"), "완제품", RegulationLevel.PROHIBITED),
            golden("A14", "거북등껍질 장신구", "야생동식물 제품", List.of("거북등껍질"), "완제품", RegulationLevel.PROHIBITED),
            // B. 주의 — 수량·용량·금액 한도나 신고를 지키면 가져올 수 있다
            golden("B1", "위스키", "주류·담배·향수", List.of("보리", "알코올"), "병입 완제품", RegulationLevel.RESTRICTED),
            golden("B2", "와인", "주류·담배·향수", List.of("포도", "알코올"), "병입 완제품", RegulationLevel.RESTRICTED),
            golden("B3", "담배", "주류·담배·향수", List.of("연초"), "궐련 완제품", RegulationLevel.RESTRICTED),
            golden("B4", "전자담배 액상", "주류·담배·향수", List.of("니코틴", "향료"), "병입 액상", RegulationLevel.RESTRICTED),
            golden("B5", "향수", "주류·담배·향수", List.of("알코올", "향료"), "병입 완제품", RegulationLevel.RESTRICTED),
            golden("B6", "진통제", "의약품·건강기능식품", List.of("아세트아미노펜"), "정제 대량", RegulationLevel.RESTRICTED),
            golden("B7", "종합비타민", "의약품·건강기능식품", List.of("비타민", "미네랄"), "병입 정제", RegulationLevel.RESTRICTED),
            golden("B8", "감기약", "의약품·건강기능식품", List.of("슈도에페드린"), "정제", RegulationLevel.RESTRICTED),
            golden("B9", "생화 꽃다발", "식물·종자·흙", List.of("꽃"), "생것", RegulationLevel.RESTRICTED),
            // C. 허용
            golden("C1", "캐릭터 인형", "일반 공산품", List.of("폴리에스터", "솜"), "완제품", RegulationLevel.ALLOWED),
            golden("C2", "티셔츠", "일반 공산품", List.of("면"), "완제품", RegulationLevel.ALLOWED),
            golden("C3", "운동화", "일반 공산품", List.of("합성섬유", "고무"), "완제품", RegulationLevel.ALLOWED),
            golden("C4", "스마트폰", "일반 공산품", List.of("금속", "유리", "배터리"), "완제품", RegulationLevel.ALLOWED),
            golden("C5", "무선 이어폰", "일반 공산품", List.of("플라스틱", "배터리"), "완제품", RegulationLevel.ALLOWED),
            golden("C6", "선글라스", "일반 공산품", List.of("플라스틱"), "완제품", RegulationLevel.ALLOWED),
            golden("C7", "로션", "일반 공산품", List.of("정제수", "글리세린"), "용기 완제품", RegulationLevel.ALLOWED),
            golden("C8", "볼펜 세트", "일반 공산품", List.of("플라스틱", "잉크"), "완제품", RegulationLevel.ALLOWED),
            golden("C9", "냉장고 자석", "일반 공산품", List.of("금속", "플라스틱"), "완제품", RegulationLevel.ALLOWED),
            golden("C10", "머그컵", "일반 공산품", List.of("도자기"), "신품 완제품", RegulationLevel.ALLOWED),
            golden("C11", "우산", "일반 공산품", List.of("금속", "폴리에스터"), "완제품", RegulationLevel.ALLOWED),
            golden("C12", "초콜릿", "가공식품·과자", List.of("카카오", "설탕"), "포장 완제품", RegulationLevel.ALLOWED),
            golden("C13", "쿠키", "가공식품·과자", List.of("밀가루", "설탕", "버터"), "포장 완제품", RegulationLevel.ALLOWED),
            golden("C14", "사탕", "가공식품·과자", List.of("설탕", "물엿"), "포장 완제품", RegulationLevel.ALLOWED),
            golden("C15", "말린 망고", "가공식품·과자", List.of("망고", "설탕"), "건조 포장", RegulationLevel.ALLOWED),
            golden("C16", "딸기잼", "가공식품·과자", List.of("딸기", "설탕"), "병입 완제품", RegulationLevel.ALLOWED),
            golden("C17", "녹차 티백", "가공식품·과자", List.of("녹차 잎"), "건조 가공 포장", RegulationLevel.ALLOWED),
            // D. 경계 사례 — 채점 제외
            golden("D1", "조미김", "수산물·수산가공품", List.of("김", "참기름", "소금"), "건조 가공 포장", null),
            golden("D2", "마른멸치", "수산물·수산가공품", List.of("멸치"), "건조", null),
            golden("D3", "볶은 커피 원두", "가공식품·과자", List.of("커피"), "볶음", null),
            golden("D4", "컵라면", "가공식품·과자", List.of("밀가루", "소고기 분말 스프"), "포장 완제품", null));

    private static GoldenCase golden(String id, String name, String category, List<String> ingredients, String state,
            RegulationLevel expected) {
        return new GoldenCase(id, new ItemAnalysis(name, category, ingredients, state), expected);
    }

    @Autowired
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @Autowired
    private BedrockRuntimeClient bedrockRuntimeClient;

    @Autowired
    private BedrockProperties properties;

    @Autowired
    private BedrockQuarantineRegulationClient client;

    private double spentWon;

    @Test
    @DisplayName("현재(top-15) vs 리랭크(top-30→top-5) — 판정 문구·리랭크 점수까지 나란히 비교한다")
    void compareCurrentVsRerank() throws Exception {
        System.out.printf("[CONFIG] model=%s numberOfResults=%d maxTokens=%d%n",
                properties.modelId(), properties.numberOfResults(), properties.maxTokens());

        StringBuilder table = new StringBuilder("# 검역 리랭크 비교 — 현재(top-%d) vs top-%d→Cohere top-%d\n\n"
                .formatted(properties.numberOfResults(), WIDE_POOL, RERANK_TOP))
                .append("판정 모델=").append(properties.modelId()).append("\n\n")
                .append("| 물품 | 현재 판정 | 현재 입력토큰 | 현재 규정길이(자) | 과태료 포함(현재) | ")
                .append("리랭크 판정 | 리랭크 입력토큰 | 리랭크 규정길이(자) | 과태료 포함(리랭크) |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        StringBuilder detail = new StringBuilder("\n# 물품별 판정 문구·리랭크 점수\n");

        SoftAssertions softly = new SoftAssertions();
        int total = CASES.size();
        int done = 0;

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (ItemAnalysis item : CASES) {
                if (spentWon > BUDGET_WON) {
                    System.out.printf("[PROGRESS] 예산 ₩%.0f 초과로 중단 — %d/%d(%.0f%%)까지만 실행됨%n",
                            BUDGET_WON, done, total, done * 100.0 / total);
                    break;
                }

                String currentRegs = client.retrieveRegulations(item);
                ConverseResponse currentResponse = converse(currentRegs, item);
                QuarantineVerdict currentVerdict = parseVerdict(currentResponse);
                spentWon += costWon(currentResponse);

                String itemQuery = BedrockQuarantineRegulationClient.itemQuery(item);
                String penaltyQuery = BedrockQuarantineRegulationClient.penaltyQuery(item);
                List<RankedChunk> itemRanked = rerankRanked(tokyo, itemQuery, retrieveWide(itemQuery));
                List<RankedChunk> penaltyRanked = rerankRanked(tokyo, penaltyQuery, retrieveWide(penaltyQuery));
                spentWon += RERANK_USD * WON_PER_USD * 2;

                String rerankRegs = topRegulationsOf(itemRanked, penaltyRanked);
                ConverseResponse rerankResponse = converse(rerankRegs, item);
                QuarantineVerdict rerankVerdict = parseVerdict(rerankResponse);
                spentWon += costWon(rerankResponse);

                table.append("| %s | %s | %d | %d | %s | %s | %d | %d | %s |\n".formatted(
                        item.name(),
                        currentVerdict.level().getLabel(), currentResponse.usage().inputTokens(),
                        currentRegs.length(), hasPenaltyAmount(currentRegs),
                        rerankVerdict.level().getLabel(), rerankResponse.usage().inputTokens(),
                        rerankRegs.length(), hasPenaltyAmount(rerankRegs)));

                detail.append(detailSectionOf(item, currentVerdict, rerankVerdict, itemQuery, itemRanked,
                        penaltyQuery, penaltyRanked));

                if (PENALTY_RISK_ITEM.equals(item.name())) {
                    softly.assertThat(hasPenaltyAmount(rerankRegs))
                            .as("리랭크 후에도 %s 발췌문에 과태료 부과기준(금액)이 남아 있어야 한다", item.name())
                            .isTrue();
                    softly.assertThat(rerankVerdict.penalty())
                            .as("리랭크 판정에도 과태료 금액 안내가 붙어야 한다")
                            .isNotNull();
                }

                done++;
                System.out.printf("[PROGRESS] %d/%d(%.0f%%) 완료 — 물품=%s 누적비용=₩%.0f%n",
                        done, total, done * 100.0 / total, item.name(), spentWon);
                write(table.toString() + detail);
            }
        }

        write(table.toString() + detail);
        System.out.println(table);
        System.out.println(detail);
        System.out.printf("[PROGRESS] 종료 — 총 누적비용=₩%.0f%n", spentWon);
        softly.assertAll();
    }

    /**
     * 실제로 걸리는 물품 10종({@link #ITEMS_FLAGGED})에 <b>품목 질의만 점수 필터(0.35)</b>를 적용하고,
     * 과태료 질의는 그대로 top-5를 쓴다 — 지난 실측 결론(과태료 질의는 점수로 못 거름)을 반영한 조합.
     * 같은 물품을 현재 방식(top-15)과 나란히 돌려 판정·토큰·품목질의 채택 청크 수를 비교한다.
     */
    @Test
    @DisplayName("실제로 걸리는 물품 10종 — 품목 질의 점수필터(0.35) vs 현재(top-15) 비교")
    void compareScoreFilterVsCurrent() throws Exception {
        System.out.printf("[CONFIG] model=%s numberOfResults=%d maxTokens=%d scoreThreshold=%.2f%n",
                properties.modelId(), properties.numberOfResults(), properties.maxTokens(), ITEM_QUERY_SCORE_THRESHOLD);

        StringBuilder table = new StringBuilder(
                "# 점수필터(품목질의 %.2f + 과태료질의 top-%d) vs 현재(top-%d)\n\n"
                        .formatted(ITEM_QUERY_SCORE_THRESHOLD, RERANK_TOP, properties.numberOfResults()))
                .append("판정 모델=").append(properties.modelId()).append("\n\n")
                .append("| 물품 | 현재 판정 | 현재 입력토큰 | 점수필터 판정 | 점수필터 입력토큰 | ")
                .append("품목질의 채택청크(0.35↑) | 품목질의 최고점수 |\n")
                .append("|---|---|---|---|---|---|---|\n");
        StringBuilder detail = new StringBuilder("\n# 물품별 판정 문구\n");

        int total = ITEMS_FLAGGED.size();
        int done = 0;

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (ItemAnalysis item : ITEMS_FLAGGED) {
                if (spentWon > BUDGET_WON) {
                    System.out.printf("[PROGRESS] 예산 ₩%.0f 초과로 중단 — %d/%d(%.0f%%)까지만 실행됨%n",
                            BUDGET_WON, done, total, done * 100.0 / total);
                    break;
                }

                String currentRegs = client.retrieveRegulations(item);
                ConverseResponse currentResponse = converse(currentRegs, item);
                QuarantineVerdict currentVerdict = parseVerdict(currentResponse);
                spentWon += costWon(currentResponse);

                String itemQuery = BedrockQuarantineRegulationClient.itemQuery(item);
                String penaltyQuery = BedrockQuarantineRegulationClient.penaltyQuery(item);
                List<RankedChunk> itemRanked = rerankRanked(tokyo, itemQuery, retrieveWide(itemQuery));
                List<RankedChunk> penaltyRanked = rerankRanked(tokyo, penaltyQuery, retrieveWide(penaltyQuery));
                spentWon += RERANK_USD * WON_PER_USD * 2;

                List<String> itemAboveThreshold = itemRanked.stream()
                        .filter(chunk -> chunk.score() >= ITEM_QUERY_SCORE_THRESHOLD)
                        .map(RankedChunk::text)
                        .toList();
                /** 필터 통과 청크가 0개면 품목 질의가 통째로 빈 채 넘어간다 — 안전장치로 top-{@link #RERANK_TOP}로 폴백한다. */
                boolean usedFallback = itemAboveThreshold.isEmpty();
                List<String> itemChunks = usedFallback
                        ? itemRanked.stream().limit(RERANK_TOP).map(RankedChunk::text).toList()
                        : itemAboveThreshold;

                Set<String> chunks = new LinkedHashSet<>(itemChunks);
                penaltyRanked.stream().limit(RERANK_TOP).map(RankedChunk::text).forEach(chunks::add);
                String filteredRegs = String.join("\n\n---\n\n", chunks);

                ConverseResponse filteredResponse = converse(filteredRegs, item);
                QuarantineVerdict filteredVerdict = parseVerdict(filteredResponse);
                spentWon += costWon(filteredResponse);

                float itemTopScore = itemRanked.isEmpty() ? 0f : itemRanked.get(0).score();
                String fallbackNote = usedFallback ? "(폴백 top-%d)".formatted(RERANK_TOP) : "";
                table.append("| %s | %s | %d | %s | %d | %d개%s | %.4f |\n".formatted(
                        item.name(),
                        currentVerdict.level().getLabel(), currentResponse.usage().inputTokens(),
                        filteredVerdict.level().getLabel(), filteredResponse.usage().inputTokens(),
                        itemChunks.size(), fallbackNote, itemTopScore));

                detail.append("""

                        ## %s (품목질의 최고점수=%.4f, 채택=%d개%s)

                        **현재(top-%d)** — 판정=%s
                        - reason: %s
                        - penalty: %s
                        - action: %s

                        **점수필터** — 판정=%s
                        - reason: %s
                        - penalty: %s
                        - action: %s
                        """.formatted(item.name(), itemTopScore, itemChunks.size(), fallbackNote,
                        properties.numberOfResults(), currentVerdict.level().getLabel(),
                        text(currentVerdict.reason()), text(currentVerdict.penalty()), text(currentVerdict.action()),
                        filteredVerdict.level().getLabel(),
                        text(filteredVerdict.reason()), text(filteredVerdict.penalty()), text(filteredVerdict.action())));

                done++;
                System.out.printf("[PROGRESS] %d/%d(%.0f%%) 완료 — 물품=%s 누적비용=₩%.0f%n",
                        done, total, done * 100.0 / total, item.name(), spentWon);
                Path out = Path.of("build", "quarantine-score-filter-comparison.md");
                Files.createDirectories(out.getParent());
                Files.writeString(out, table.toString() + detail);
            }
        }

        Path out = Path.of("build", "quarantine-score-filter-comparison.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, table.toString() + detail);
        System.out.println(table);
        System.out.println(detail);
        System.out.printf("[PROGRESS] 종료 — 총 누적비용=₩%.0f%n", spentWon);
    }

    /**
     * 물품 5개 × 질의 2개(품목·과태료) 전부 top-10 청크 전문을 찍는다 — 생성 호출 없이 검색·리랭크만이라
     * 거의 무료다. 사람이 읽고 "의미 있는 청크가 몇 점부터인지" 판단하기 위한 원자료.
     */
    @Test
    @DisplayName("물품 5개 전부 — 품목·과태료 질의 top-10 청크 전문을 출력한다")
    void printAllChunksWithScores() throws Exception {
        StringBuilder report = new StringBuilder("# 물품 5개 — 품목·과태료 질의 top-10 청크 전문\n");

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (ItemAnalysis item : CASES) {
                String itemQuery = BedrockQuarantineRegulationClient.itemQuery(item);
                String penaltyQuery = BedrockQuarantineRegulationClient.penaltyQuery(item);
                List<RankedChunk> itemRanked = rerankRanked(tokyo, itemQuery, retrieveWide(itemQuery));
                List<RankedChunk> penaltyRanked = rerankRanked(tokyo, penaltyQuery, retrieveWide(penaltyQuery));

                report.append("\n## %s\n\n### 품목 질의: %s\n\n".formatted(item.name(), itemQuery));
                itemRanked.stream().limit(10).forEach(chunk -> report.append(
                        "**%d위 (점수 %.4f)**\n\n%s\n\n---\n\n".formatted(chunk.rank(), chunk.score(), chunk.text())));

                report.append("\n### 과태료 질의: %s\n\n".formatted(penaltyQuery));
                penaltyRanked.stream().limit(10).forEach(chunk -> report.append(
                        "**%d위 (점수 %.4f)**\n\n%s\n\n---\n\n".formatted(chunk.rank(), chunk.score(), chunk.text())));

                System.out.printf("[PROGRESS] %s 완료%n", item.name());
            }
        }

        Path out = Path.of("build", "quarantine-chunk-scores-full.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.println(report);
    }

    /**
     * {@link #CASES_ROUND2} 10종 × 질의 2개 — top-15까지 보여준다(1라운드보다 넓게, 니치
     * 카테고리는 유의미한 청크가 10위 밖일 수 있어서). 생성 호출 없이 검색·리랭크만이라 거의 무료다.
     */
    @Test
    @DisplayName("2라운드 물품 10개 — 품목·과태료 질의 top-15 청크 전문을 출력한다")
    void printRound2ChunksWithScores() throws Exception {
        StringBuilder report = new StringBuilder("# 2라운드 물품 10개 — 품목·과태료 질의 top-15 청크 전문\n");

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (ItemAnalysis item : CASES_ROUND2) {
                String itemQuery = BedrockQuarantineRegulationClient.itemQuery(item);
                String penaltyQuery = BedrockQuarantineRegulationClient.penaltyQuery(item);
                List<RankedChunk> itemRanked = rerankRanked(tokyo, itemQuery, retrieveWide(itemQuery));
                List<RankedChunk> penaltyRanked = rerankRanked(tokyo, penaltyQuery, retrieveWide(penaltyQuery));

                report.append("\n## %s\n\n### 품목 질의: %s\n\n".formatted(item.name(), itemQuery));
                itemRanked.stream().limit(15).forEach(chunk -> report.append(
                        "**%d위 (점수 %.4f)**\n\n%s\n\n---\n\n".formatted(chunk.rank(), chunk.score(), chunk.text())));

                report.append("\n### 과태료 질의: %s\n\n".formatted(penaltyQuery));
                penaltyRanked.stream().limit(15).forEach(chunk -> report.append(
                        "**%d위 (점수 %.4f)**\n\n%s\n\n---\n\n".formatted(chunk.rank(), chunk.score(), chunk.text())));

                System.out.printf("[PROGRESS] %s 완료%n", item.name());
            }
        }

        Path out = Path.of("build", "quarantine-chunk-scores-round2.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.println(report);
    }

    /** 위스키 과태료 질의의 리랭크 순위 1~10위 청크 전문을 그대로 찍는다 — 생성 호출 없이 검색·리랭크만이라 거의 무료다. */
    @Test
    @DisplayName("위스키 과태료 질의 — top-10 청크 전문을 출력한다")
    void printWhiskyPenaltyChunks() throws Exception {
        ItemAnalysis whisky = CASES.stream()
                .filter(item -> item.name().equals("위스키"))
                .findFirst()
                .orElseThrow();
        String penaltyQuery = BedrockQuarantineRegulationClient.penaltyQuery(whisky);

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            List<RankedChunk> ranked = rerankRanked(tokyo, penaltyQuery, retrieveWide(penaltyQuery));

            StringBuilder report = new StringBuilder("# 위스키 과태료 질의 — top-10 청크 전문\n\n질의: %s\n\n"
                    .formatted(penaltyQuery));
            ranked.stream().limit(10).forEach(chunk -> report.append(
                    "## %d위 (점수 %.4f, 과태료패턴=%s)\n\n%s\n\n---\n\n"
                            .formatted(chunk.rank(), chunk.score(), chunk.hasPenaltyAmount(), chunk.text())));

            Path out = Path.of("build", "whisky-penalty-chunks.md");
            Files.createDirectories(out.getParent());
            Files.writeString(out, report.toString());
            System.out.println(report);
        }
    }

    /**
     * 골든셋 44종 — 현재 방식(품목·과태료 모두 벡터 top-15)과 변경안(품목 질의만 top-50 → 리랭크 →
     * 0.3 이상 top-5, 과태료 질의는 그대로)을 나란히 판정한다. 변경안에서 품목 청크가 0개가 되면
     * 점수와 상관없이 top-{@link #GOLDEN_FALLBACK_TOP}을 넣은 판정도 한 번 더 받아, 0개일 때의 처리를
     * 정하는 근거로 쓴다. 결과는 {@code build/quarantine-golden-set.md}에 남는다.
     */
    @Test
    @DisplayName("골든셋 44종 — 품목 질의만 리랭크(top-50→0.3↑ top-5) vs 현재(top-15) 비교")
    void goldenSetItemQueryRerankOnly() throws Exception {
        System.out.printf("[CONFIG] model=%s numberOfResults=%d pool=%d top=%d threshold=%.2f%n",
                properties.modelId(), properties.numberOfResults(), GOLDEN_POOL, GOLDEN_TOP, GOLDEN_SCORE_THRESHOLD);

        StringBuilder table = new StringBuilder()
                .append("| # | 물품 | 기대 | 현재 판정 | 현재 결과 | 변경안 판정 | 변경안 결과 | 채택 청크 | 최고 점수 | ")
                .append("현재 입력토큰 | 변경안 입력토큰 | 0개일 때 top-%d 판정 |\n".formatted(GOLDEN_FALLBACK_TOP))
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        StringBuilder detail = new StringBuilder("\n# 물품별 판정 문구·리랭크 top-%d\n".formatted(GOLDEN_TOP));
        Map<String, Integer> currentOutcomes = new LinkedHashMap<>();
        Map<String, Integer> rerankOutcomes = new LinkedHashMap<>();

        List<GoldenCase> cases = selectedGoldenCases();
        int total = cases.size();
        int done = 0;

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (GoldenCase golden : cases) {
                if (spentWon > GOLDEN_BUDGET_WON) {
                    System.out.printf("[PROGRESS] 예산 ₩%.0f 초과로 중단 — %d/%d(%.0f%%)까지만 실행됨%n",
                            GOLDEN_BUDGET_WON, done, total, done * 100.0 / total);
                    break;
                }
                ItemAnalysis item = golden.item();

                String currentRegs = client.retrieveRegulations(item);
                ConverseResponse currentResponse = converse(currentRegs, item);
                QuarantineVerdict currentVerdict = parseVerdict(currentResponse);
                spentWon += costWon(currentResponse);

                String itemQuery = BedrockQuarantineRegulationClient.itemQuery(item);
                String penaltyQuery = BedrockQuarantineRegulationClient.penaltyQuery(item);
                List<RankedChunk> itemTop = rerankRanked(tokyo, itemQuery, retrieve(itemQuery, GOLDEN_POOL)).stream()
                        .limit(GOLDEN_TOP)
                        .toList();
                spentWon += RERANK_USD * WON_PER_USD;
                List<String> adopted = itemTop.stream()
                        .filter(chunk -> chunk.score() >= GOLDEN_SCORE_THRESHOLD)
                        .map(RankedChunk::text)
                        .toList();
                List<String> penaltyChunks = retrieve(penaltyQuery, properties.numberOfResults());

                String rerankRegs = joinDistinct(adopted, penaltyChunks);
                ConverseResponse rerankResponse = converse(rerankRegs, item);
                QuarantineVerdict rerankVerdict = parseVerdict(rerankResponse);
                spentWon += costWon(rerankResponse);

                String fallbackLabel = "-";
                QuarantineVerdict fallbackVerdict = null;
                if (adopted.isEmpty()) {
                    List<String> fallbackItem = itemTop.stream()
                            .limit(GOLDEN_FALLBACK_TOP)
                            .map(RankedChunk::text)
                            .toList();
                    ConverseResponse fallbackResponse = converse(joinDistinct(fallbackItem, penaltyChunks), item);
                    fallbackVerdict = parseVerdict(fallbackResponse);
                    spentWon += costWon(fallbackResponse);
                    fallbackLabel = fallbackVerdict.level().getLabel()
                            + " (" + outcomeOf(golden.expected(), fallbackVerdict.level()) + ")";
                }

                String currentOutcome = outcomeOf(golden.expected(), currentVerdict.level());
                String rerankOutcome = outcomeOf(golden.expected(), rerankVerdict.level());
                currentOutcomes.merge(currentOutcome, 1, Integer::sum);
                rerankOutcomes.merge(rerankOutcome, 1, Integer::sum);

                float topScore = itemTop.isEmpty() ? 0f : itemTop.get(0).score();
                table.append("| %s | %s | %s | %s | %s | %s | %s | %d개 | %.4f | %d | %d | %s |\n".formatted(
                        golden.id(), item.name(), expectedLabel(golden.expected()),
                        currentVerdict.level().getLabel(), currentOutcome,
                        rerankVerdict.level().getLabel(), rerankOutcome,
                        adopted.size(), topScore,
                        currentResponse.usage().inputTokens(), rerankResponse.usage().inputTokens(),
                        fallbackLabel));

                detail.append(goldenDetailOf(golden, itemQuery, itemTop, penaltyChunks.size(),
                        currentVerdict, rerankVerdict, fallbackVerdict));

                done++;
                System.out.printf("[PROGRESS] %d/%d(%.0f%%) 완료 — 물품=%s 누적비용=₩%.0f%n",
                        done, total, done * 100.0 / total, item.name(), spentWon);
                writeGolden(goldenHeader(currentOutcomes, rerankOutcomes, done, total) + table + detail);
            }
        }

        String report = goldenHeader(currentOutcomes, rerankOutcomes, done, total) + table + detail;
        writeGolden(report);
        System.out.println(report);
        System.out.printf("[PROGRESS] 종료 — 총 누적비용=₩%.0f%n", spentWon);
    }

    private static final Path BASIS_REPORT = Path.of("검역_골든셋_결과.md");
    private static final double BASIS_BUDGET_WON = 8_000;
    private static final int PREVIEW_CHARS = 200;

    /** 판정 + 모델이 스스로 밝힌 근거(발췌문 인용 또는 프롬프트 규칙). */
    private record BasisVerdict(RegulationLevel level, String reason, String penalty, String action,
            String basis, String quote, String rule, int inputTokens) {
    }

    /** 발췌문에 들어간 청크 하나와 그 출처(어느 질의의 몇 위였는지). */
    private record SourcedChunk(String source, String text) {
    }

    /**
     * 골든셋 44종 — 검색 방식(기존 벡터 top-k vs 품목 질의만 리랭크)을 비교하되, 판정마다 모델이 근거를
     * 밝히게 한다. 운영 도구 스키마에 {@code basis}·{@code basis_quote}·{@code basis_rule} 세 필드만
     * 더했고, 인용문을 발췌문 청크와 대조해 "어느 질의 몇 위 청크"였는지 찾아 적는다.
     * 결과는 백엔드 루트의 {@code 검역_골든셋_결과.md}에 남는다.
     */
    @Test
    @DisplayName("골든셋 44종 — 판정 근거(청크/프롬프트 규칙)와 리랭크 top-5 청크까지 기록한다")
    void goldenSetWithBasis() throws Exception {
        List<GoldenCase> cases = selectedGoldenCases();
        StringBuilder summary = new StringBuilder()
                .append("| # | 물품 | 기대 | 기존 판정 | 기존 근거 | 리랭크 판정 | 리랭크 근거 | 채택 청크 | 최고 점수 |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        StringBuilder detail = new StringBuilder();
        Map<String, Integer> currentOutcomes = new LinkedHashMap<>();
        Map<String, Integer> rerankOutcomes = new LinkedHashMap<>();
        int done = 0;

        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (GoldenCase golden : cases) {
                if (spentWon > BASIS_BUDGET_WON) {
                    System.out.printf("[PROGRESS] 예산 ₩%.0f 초과로 중단 — %d/%d%n", BASIS_BUDGET_WON, done, cases.size());
                    break;
                }
                ItemAnalysis item = golden.item();
                String itemQuery = BedrockQuarantineRegulationClient.itemQuery(item);
                String penaltyQuery = BedrockQuarantineRegulationClient.penaltyQuery(item);

                List<String> itemVector = retrieve(itemQuery, properties.numberOfResults());
                List<String> penaltyVector = retrieve(penaltyQuery, properties.numberOfResults());
                List<SourcedChunk> currentSources = new ArrayList<>(sourced("품목 벡터", itemVector));
                currentSources.addAll(sourced("과태료 벡터", penaltyVector));
                BasisVerdict current = converseWithBasis(joinDistinct(itemVector, penaltyVector), item);

                List<RankedChunk> itemTop = rerankRanked(tokyo, itemQuery, retrieve(itemQuery, GOLDEN_POOL)).stream()
                        .limit(GOLDEN_TOP)
                        .toList();
                spentWon += RERANK_USD * WON_PER_USD;
                List<RankedChunk> adopted = itemTop.stream()
                        .filter(chunk -> chunk.score() >= GOLDEN_SCORE_THRESHOLD)
                        .toList();
                List<SourcedChunk> rerankSources = new ArrayList<>(adopted.stream()
                        .map(chunk -> new SourcedChunk("품목 리랭크 %d위(%.4f)".formatted(chunk.rank(), chunk.score()),
                                chunk.text()))
                        .toList());
                rerankSources.addAll(sourced("과태료 벡터", penaltyVector));
                BasisVerdict rerank = converseWithBasis(
                        joinDistinct(adopted.stream().map(RankedChunk::text).toList(), penaltyVector), item);

                String currentOutcome = outcomeOf(golden.expected(), current.level());
                String rerankOutcome = outcomeOf(golden.expected(), rerank.level());
                currentOutcomes.merge(currentOutcome, 1, Integer::sum);
                rerankOutcomes.merge(rerankOutcome, 1, Integer::sum);

                SourcedChunk currentUsed = matchQuote(current.quote(), currentSources);
                SourcedChunk rerankUsed = matchQuote(rerank.quote(), rerankSources);
                float topScore = itemTop.isEmpty() ? 0f : itemTop.get(0).score();
                summary.append("| %s | %s | %s | %s (%s) | %s | %s (%s) | %s | %d개 | %.4f |\n".formatted(
                        golden.id(), item.name(), expectedLabel(golden.expected()),
                        current.level().getLabel(), currentOutcome, shortBasis(current, currentUsed),
                        rerank.level().getLabel(), rerankOutcome, shortBasis(rerank, rerankUsed),
                        adopted.size(), topScore));

                detail.append(basisDetailOf(golden, itemQuery, itemTop, current, currentUsed, currentOutcome,
                        rerank, rerankUsed, rerankOutcome));

                done++;
                System.out.printf("[PROGRESS] %d/%d(%.0f%%) 완료 — 물품=%s 누적비용=₩%.0f%n",
                        done, cases.size(), done * 100.0 / cases.size(), item.name(), spentWon);
                writeBasisReport(basisHeader(currentOutcomes, rerankOutcomes, done, cases.size()), summary, detail);
            }
        }
        writeBasisReport(basisHeader(currentOutcomes, rerankOutcomes, done, cases.size()), summary, detail);
        System.out.printf("[PROGRESS] 종료 — 총 누적비용=₩%.0f%n", spentWon);
    }

    /**
     * 리랭크 방식만 다시 판정한다 — 채택 기준 점수({@code GOLDEN_THRESHOLD}, 기본 0.30)를 바꿔 볼 때 쓴다.
     * 기존 방식 판정은 이미 있으니 부르지 않는다. {@code GOLDEN_IDS}로 물품을 반드시 골라야 하고,
     * 결과는 {@code build/quarantine-threshold-check.md}에 남는다.
     */
    @Test
    @DisplayName("지정한 물품만 — 리랭크 방식 판정을 채택 기준 점수를 바꿔 다시 받는다")
    void rerankOnlyWithThreshold() throws Exception {
        String ids = System.getenv("GOLDEN_IDS");
        if (ids == null || ids.isBlank()) {
            throw new IllegalStateException("GOLDEN_IDS로 물품을 지정해야 한다 — 전체 실행 방지");
        }
        String thresholdEnv = System.getenv("GOLDEN_THRESHOLD");
        float threshold = thresholdEnv == null ? GOLDEN_SCORE_THRESHOLD : Float.parseFloat(thresholdEnv);
        double budgetWon = 500;

        StringBuilder report = new StringBuilder("# 리랭크 채택 기준 %.2f 재판정\n\n판정 모델 `%s` · 과태료 질의 벡터 top-%d\n"
                .formatted(threshold, properties.modelId(), properties.numberOfResults()));
        try (BedrockAgentRuntimeClient tokyo = BedrockAgentRuntimeClient.builder()
                .region(Region.AP_NORTHEAST_1).build()) {
            for (GoldenCase golden : selectedGoldenCases()) {
                if (spentWon > budgetWon) {
                    System.out.printf("[PROGRESS] 예산 ₩%.0f 초과로 중단%n", budgetWon);
                    break;
                }
                ItemAnalysis item = golden.item();
                String itemQuery = BedrockQuarantineRegulationClient.itemQuery(item);
                String penaltyQuery = BedrockQuarantineRegulationClient.penaltyQuery(item);
                List<RankedChunk> itemTop = rerankRanked(tokyo, itemQuery, retrieve(itemQuery, GOLDEN_POOL)).stream()
                        .limit(GOLDEN_TOP)
                        .toList();
                spentWon += RERANK_USD * WON_PER_USD;
                List<RankedChunk> adopted = itemTop.stream().filter(chunk -> chunk.score() >= threshold).toList();
                List<String> penaltyVector = retrieve(penaltyQuery, properties.numberOfResults());

                List<SourcedChunk> sources = new ArrayList<>(adopted.stream()
                        .map(chunk -> new SourcedChunk("품목 리랭크 %d위(%.4f)".formatted(chunk.rank(), chunk.score()),
                                chunk.text()))
                        .toList());
                sources.addAll(sourced("과태료 벡터", penaltyVector));
                BasisVerdict verdict = converseWithBasis(
                        joinDistinct(adopted.stream().map(RankedChunk::text).toList(), penaltyVector), item);
                SourcedChunk used = matchQuote(verdict.quote(), sources);

                report.append("\n## %s %s (기대: %s)\n\n**리랭크 점수 (품목 질의 top-%d)** — `%s`\n\n"
                                .formatted(golden.id(), item.name(), expectedLabel(golden.expected()), GOLDEN_TOP, itemQuery))
                        .append("| 순위 | 점수 | 채택(≥%.2f) | 판정에 사용 |\n|---|---|---|---|\n".formatted(threshold));
                for (RankedChunk chunk : itemTop) {
                    report.append("| %d | %.4f | %s | %s |\n".formatted(chunk.rank(), chunk.score(),
                            chunk.score() >= threshold ? "O" : "X",
                            used != null && used.text().equals(chunk.text()) ? "**O**" : ""));
                }
                report.append("\n**채택된 청크 내용** (%d개)\n\n".formatted(adopted.size()));
                for (RankedChunk chunk : adopted) {
                    report.append("%d위 (%.4f)\n\n```\n%s\n```\n\n".formatted(chunk.rank(), chunk.score(), chunk.text()));
                }
                report.append("""
                        **리랭크 후 결과**

                        - **판정: %s** (%s)
                        - 근거: %s — %s
                        - reason: %s
                        - penalty: %s
                        - action: %s
                        - 입력 토큰: %,d
                        """.formatted(verdict.level().getLabel(), outcomeOf(golden.expected(), verdict.level()),
                        basisKind(verdict.basis()), basisContent(verdict, used),
                        text(verdict.reason()), text(verdict.penalty()), text(verdict.action()), verdict.inputTokens()));

                System.out.printf("[PROGRESS] %s 완료 — 판정=%s 채택=%d개 누적비용=₩%.0f%n",
                        item.name(), verdict.level().getLabel(), adopted.size(), spentWon);
                Path out = Path.of("build", "quarantine-threshold-check.md");
                Files.createDirectories(out.getParent());
                Files.writeString(out, report.toString());
            }
        }
        System.out.printf("[PROGRESS] 종료 — 총 누적비용=₩%.0f%n", spentWon);
    }

    /**
     * 기존 방식(품목·과태료 질의 모두 벡터 top-{@code properties.numberOfResults()})만 다시 판정한다 — 프롬프트를
     * 고친 뒤 기존 방식 결과를 맞출 때 쓴다. {@code GOLDEN_IDS}로 물품을 반드시 골라야 하고,
     * 결과는 {@code build/quarantine-current-check.md}에 남는다.
     */
    @Test
    @DisplayName("지정한 물품만 — 기존 방식 판정을 근거와 함께 다시 받는다")
    void currentOnlyWithBasis() throws Exception {
        String ids = System.getenv("GOLDEN_IDS");
        if (ids == null || ids.isBlank()) {
            throw new IllegalStateException("GOLDEN_IDS로 물품을 지정해야 한다 — 전체 실행 방지");
        }
        double budgetWon = 500;

        StringBuilder report = new StringBuilder("# 기존 방식 재판정\n\n판정 모델 `%s` · 품목·과태료 질의 벡터 top-%d\n"
                .formatted(properties.modelId(), properties.numberOfResults()));
        for (GoldenCase golden : selectedGoldenCases()) {
            if (spentWon > budgetWon) {
                System.out.printf("[PROGRESS] 예산 ₩%.0f 초과로 중단%n", budgetWon);
                break;
            }
            ItemAnalysis item = golden.item();
            List<String> itemVector = retrieve(BedrockQuarantineRegulationClient.itemQuery(item),
                    properties.numberOfResults());
            List<String> penaltyVector = retrieve(BedrockQuarantineRegulationClient.penaltyQuery(item),
                    properties.numberOfResults());
            List<SourcedChunk> sources = new ArrayList<>(sourced("품목 벡터", itemVector));
            sources.addAll(sourced("과태료 벡터", penaltyVector));
            BasisVerdict verdict = converseWithBasis(joinDistinct(itemVector, penaltyVector), item);
            SourcedChunk used = matchQuote(verdict.quote(), sources);

            report.append("""

                    ## %s %s (기대: %s)

                    - **판정: %s** (%s)
                    - 근거: %s — %s
                    - reason: %s
                    - penalty: %s
                    - action: %s
                    - 입력 토큰: %,d
                    """.formatted(golden.id(), item.name(), expectedLabel(golden.expected()),
                    verdict.level().getLabel(), outcomeOf(golden.expected(), verdict.level()),
                    basisKind(verdict.basis()), basisContent(verdict, used),
                    text(verdict.reason()), text(verdict.penalty()), text(verdict.action()), verdict.inputTokens()));

            System.out.printf("[PROGRESS] %s 완료 — 판정=%s 누적비용=₩%.0f%n",
                    item.name(), verdict.level().getLabel(), spentWon);
            Path out = Path.of("build", "quarantine-current-check.md");
            Files.createDirectories(out.getParent());
            Files.writeString(out, report.toString());
        }
        System.out.printf("[PROGRESS] 종료 — 총 누적비용=₩%.0f%n", spentWon);
    }

    /**
     * 골든셋 40종(경계 사례 제외)의 품목 질의를 벡터 검색 top-5로만 받아 전문을 남긴다 — 리랭크 top-5와 비교하려는
     * 원자료다. 리랭크·판정 모델은 부르지 않는다(Knowledge Base 검색만). 결과는 {@code build/quarantine-vector-top5.md}.
     */
    @Test
    @DisplayName("골든셋 40종 — 품목 질의 벡터 top-5만 받는다 (리랭크·판정 호출 없음)")
    void vectorTop5Only() throws Exception {
        StringBuilder report = new StringBuilder("# 품목 질의 벡터 top-5 (리랭크 없음)\n");
        for (GoldenCase golden : GOLDEN_SET) {
            if (golden.expected() == null) {
                continue;
            }
            String itemQuery = BedrockQuarantineRegulationClient.itemQuery(golden.item());
            List<String> top = retrieve(itemQuery, GOLDEN_TOP);
            report.append("\n## %s %s\n\n`%s`\n".formatted(golden.id(), golden.item().name(), itemQuery));
            for (int i = 0; i < top.size(); i++) {
                report.append("\n**%d위**\n\n```\n%s\n```\n".formatted(i + 1, top.get(i)));
            }
            System.out.printf("[PROGRESS] %s 완료%n", golden.item().name());
        }
        Path out = Path.of("build", "quarantine-vector-top5.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
    }

    /**
     * 지정한 물품의 기존 방식 검색 결과(품목·과태료 질의 각 벡터 top-{@code properties.numberOfResults()})를
     * 전문으로 남긴다 — 판정에 어떤 청크가 들어갔는지 확인하는 용도. 리랭크·판정 모델은 부르지 않는다.
     * 결과는 {@code build/quarantine-current-chunks.md}.
     */
    @Test
    @DisplayName("지정한 물품만 — 기존 방식 검색 청크 전문을 받는다 (리랭크·판정 호출 없음)")
    void currentChunksOnly() throws Exception {
        String ids = System.getenv("GOLDEN_IDS");
        if (ids == null || ids.isBlank()) {
            throw new IllegalStateException("GOLDEN_IDS로 물품을 지정해야 한다 — 전체 실행 방지");
        }
        StringBuilder report = new StringBuilder("# 기존 방식 검색 청크 (벡터 top-%d)\n"
                .formatted(properties.numberOfResults()));
        for (GoldenCase golden : selectedGoldenCases()) {
            ItemAnalysis item = golden.item();
            for (String query : List.of(BedrockQuarantineRegulationClient.itemQuery(item),
                    BedrockQuarantineRegulationClient.penaltyQuery(item))) {
                List<String> chunks = retrieve(query, properties.numberOfResults());
                report.append("\n## %s %s — `%s`\n".formatted(golden.id(), item.name(), query));
                for (int i = 0; i < chunks.size(); i++) {
                    report.append("\n**%d위**\n\n```\n%s\n```\n".formatted(i + 1, chunks.get(i)));
                }
            }
            System.out.printf("[PROGRESS] %s 완료%n", item.name());
        }
        Path out = Path.of("build", "quarantine-current-chunks.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
    }

    /**
     * 리랭크 없이 품목 질의를 벡터 top-{@code GOLDEN_ITEM_TOPK}(기본 5)로만 받아 판정한다 — 리랭크 top-5와 비교해
     * "리랭크가 의미 있는가"를 보려는 것이다. 과태료 질의는 {@code GOLDEN_PENALTY_TOPK}(기본
     * {@code properties.numberOfResults()})로 받는다. 리랭크는 부르지 않는다. {@code GOLDEN_IDS}로 물품을 반드시 골라야 하고,
     * 결과는 {@code build/quarantine-vector-topk-verdict.md}.
     */
    @Test
    @DisplayName("지정한 물품만 — 품목 질의 벡터 top-k(리랭크 없음)로 판정한다")
    void vectorTopkVerdict() throws Exception {
        String ids = System.getenv("GOLDEN_IDS");
        if (ids == null || ids.isBlank()) {
            throw new IllegalStateException("GOLDEN_IDS로 물품을 지정해야 한다 — 전체 실행 방지");
        }
        int itemTopk = envInt("GOLDEN_ITEM_TOPK", GOLDEN_TOP);
        int penaltyTopk = envInt("GOLDEN_PENALTY_TOPK", properties.numberOfResults());
        double budgetWon = 500;

        StringBuilder report = new StringBuilder("# 품목 질의 벡터 top-%d · 과태료 질의 벡터 top-%d (리랭크 없음)\n\n판정 모델 `%s`\n"
                .formatted(itemTopk, penaltyTopk, properties.modelId()));
        for (GoldenCase golden : selectedGoldenCases()) {
            if (spentWon > budgetWon) {
                System.out.printf("[PROGRESS] 예산 ₩%.0f 초과로 중단%n", budgetWon);
                break;
            }
            ItemAnalysis item = golden.item();
            String itemQuery = BedrockQuarantineRegulationClient.itemQuery(item);
            List<String> itemVector = retrieve(itemQuery, itemTopk);
            List<String> penaltyVector = retrieve(BedrockQuarantineRegulationClient.penaltyQuery(item), penaltyTopk);
            List<SourcedChunk> sources = new ArrayList<>(sourced("품목 벡터", itemVector));
            sources.addAll(sourced("과태료 벡터", penaltyVector));
            BasisVerdict verdict = converseWithBasis(joinDistinct(itemVector, penaltyVector), item);
            SourcedChunk used = matchQuote(verdict.quote(), sources);

            report.append("\n## %s %s (기대: %s)\n\n**품목 질의 벡터 top-%d** — `%s`\n"
                    .formatted(golden.id(), item.name(), expectedLabel(golden.expected()), itemTopk, itemQuery));
            for (int i = 0; i < itemVector.size(); i++) {
                report.append("\n%d위\n\n```\n%s\n```\n".formatted(i + 1, itemVector.get(i)));
            }
            report.append("""

                    - **판정: %s** (%s)
                    - 근거: %s — %s
                    - reason: %s
                    - penalty: %s
                    - action: %s
                    - 입력 토큰: %,d
                    """.formatted(verdict.level().getLabel(), outcomeOf(golden.expected(), verdict.level()),
                    basisKind(verdict.basis()), basisContent(verdict, used),
                    text(verdict.reason()), text(verdict.penalty()), text(verdict.action()), verdict.inputTokens()));

            System.out.printf("[PROGRESS] %s 완료 — 판정=%s 누적비용=₩%.0f%n",
                    item.name(), verdict.level().getLabel(), spentWon);
            Path out = Path.of("build", "quarantine-vector-topk-verdict.md");
            Files.createDirectories(out.getParent());
            Files.writeString(out, report.toString());
        }
        System.out.printf("[PROGRESS] 종료 — 총 누적비용=₩%.0f%n", spentWon);
    }

    private static int envInt(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    }

    private String basisHeader(Map<String, Integer> currentOutcomes, Map<String, Integer> rerankOutcomes, int done,
            int total) {
        StringBuilder header = new StringBuilder("""
                # 검역 골든셋 결과 — 기존 검색 vs 품목 질의 리랭크 (프롬프트 수정안 기준)

                > 자동 생성: `QuarantineRerankVerdictManualIT.goldenSetWithBasis` · 판정 모델 `%s` · 진행 %d/%d종 · 누적 비용 약 ₩%.0f

                ## 1. 골든셋 조건

                - 사진 분석(VLM)은 건너뛰고 `ItemAnalysis(name, category, ingredients, state)`를 직접 넣었다. 물품 목록은 `검역_리랭크_골든셋.md` 참고.
                - 등급 기준: 과태료 유무가 아니라 **"여행자가 조건을 맞추면 가져올 수 있는가"**.
                  - 금지: 맞출 수 있는 조건이 없다 (육포, 생과일, 흙, 멸종위기종)
                  - 제한(주의): 수량·용량·금액 한도나 신고를 지키면 가능하다 (술, 담배, 의약품)
                  - 허용: 제한 없음
                - 채점: **놓침**(금지·제한을 허용으로) 0건이 필수. 그 밖에 금지 약화(금지→제한), 과잉 판정(허용→제한·금지), 등급 불일치를 센다. D 그룹(경계 사례)은 채점하지 않는다.

                ## 2. 실험 조건

                | 항목 | 기존 | 리랭크 |
                |---|---|---|
                | 품목 질의 | 벡터 top-%d | 벡터 top-%d → Cohere Rerank 3.5 → 점수 %.2f 이상 중 top-%d |
                | 과태료 질의 | 벡터 top-%d | 벡터 top-%d (기존과 같음, 리랭크 없음) |
                | 시스템 프롬프트 | 수정안 | 수정안 |

                두 방식 모두 **수정된 프롬프트**로 판정했다. 차이는 검색 방식뿐이다.

                프롬프트 수정 내용 (`BedrockQuarantineRegulationClient.SYSTEM_PROMPT`):
                1. 식물검역증도 검역증명서처럼 휴대품에는 없는 서류로 본다. 이를 이유로 금지를 제한으로 낮추지 않는다.
                2. 생과실·신선 채소 규정은 생것에만 적용한다. 말린 과일·잼 등 가공품에는 적용하지 않는다.
                3. "검역증명서 미첨부 과태료 표 → 금지" 규칙은 그 표의 대상에 물품이 실제로 해당할 때만 쓴다. 동물 생산물 과태료 표를 식물성 가공식품·해조류에 적용하지 않는다.
                4. 물품이 해당하지 않는 과태료 표나 일반 검역 절차만 있으면 "근거 없음"으로 보고 분류 규칙을 따른다.

                ## 3. 판정 근거를 어떻게 적었나

                판정 도구에 세 필드를 더해 모델이 **판정과 같은 호출에서** 근거를 밝히게 했다(운영 스키마에는 없음).
                - `basis`: CHUNK(발췌문 청크) / PROMPT_RULE(시스템 프롬프트 규칙) / BOTH
                - `basis_quote`: 청크가 근거면 발췌문의 핵심 문장을 그대로 인용 → 테스트가 이 인용을 청크와 대조해 "어느 질의 몇 위 청크"인지 찾는다
                - `basis_rule`: 프롬프트 규칙이 근거면 어떤 규칙인지

                모델이 스스로 밝힌 근거라서 실제 판단 과정과 완전히 같다고 보장할 수는 없다. 인용문이 발췌문에서 발견되지 않으면 "인용 불일치"로 적었다.

                ## 4. 채점 요약

                | 결과 | 기존 | 리랭크 |
                |---|---|---|
                """.formatted(properties.modelId(), done, total, spentWon,
                properties.numberOfResults(), GOLDEN_POOL, GOLDEN_SCORE_THRESHOLD, GOLDEN_TOP,
                properties.numberOfResults(), properties.numberOfResults()));
        for (String outcome : List.of("일치", "놓침", "금지 약화", "과잉 판정", "등급 불일치", "채점 제외")) {
            header.append("| %s | %d | %d |\n".formatted(outcome,
                    currentOutcomes.getOrDefault(outcome, 0), rerankOutcomes.getOrDefault(outcome, 0)));
        }
        return header.append("\n## 5. 물품별 요약\n\n").toString();
    }

    private String basisDetailOf(GoldenCase golden, String itemQuery, List<RankedChunk> itemTop,
            BasisVerdict current, SourcedChunk currentUsed, String currentOutcome,
            BasisVerdict rerank, SourcedChunk rerankUsed, String rerankOutcome) {
        StringBuilder section = new StringBuilder("""

                ### %s %s (기대: %s)

                `%s` · 원재료 %s · 상태 %s

                | | 기존 (벡터 top-%d) | 리랭크 |
                |---|---|---|
                | 판정 | %s (%s) | %s (%s) |
                | 근거 종류 | %s | %s |
                | 근거 내용 | %s | %s |
                | reason | %s | %s |
                | penalty | %s | %s |
                | action | %s | %s |
                | 입력 토큰 | %d | %d |

                **품목 질의 리랭크 top-%d** — `%s`

                | 순위 | 점수 | 채택(≥%.2f) | 리랭크 판정에 사용 | 청크 내용 (앞 %d자) |
                |---|---|---|---|---|
                """.formatted(
                golden.id(), golden.item().name(), expectedLabel(golden.expected()),
                golden.item().category(), String.join(", ", golden.item().ingredients()), golden.item().state(),
                properties.numberOfResults(),
                current.level().getLabel(), currentOutcome, rerank.level().getLabel(), rerankOutcome,
                basisKind(current.basis()), basisKind(rerank.basis()),
                cell(basisContent(current, currentUsed)), cell(basisContent(rerank, rerankUsed)),
                cell(text(current.reason())), cell(text(rerank.reason())),
                cell(text(current.penalty())), cell(text(rerank.penalty())),
                cell(text(current.action())), cell(text(rerank.action())),
                current.inputTokens(), rerank.inputTokens(),
                GOLDEN_TOP, itemQuery, GOLDEN_SCORE_THRESHOLD, PREVIEW_CHARS));

        for (RankedChunk chunk : itemTop) {
            boolean usedByRerank = rerankUsed != null && rerankUsed.text().equals(chunk.text());
            section.append("| %d | %.4f | %s | %s | %s |\n".formatted(chunk.rank(), chunk.score(),
                    chunk.score() >= GOLDEN_SCORE_THRESHOLD ? "O" : "X", usedByRerank ? "**O**" : "",
                    cell(truncate(chunk.text(), PREVIEW_CHARS))));
        }
        if (itemTop.isEmpty()) {
            section.append("| - | - | - | - | 검색 결과 없음 |\n");
        }

        section.append("\n<details><summary>top-%d 청크 전문</summary>\n\n".formatted(GOLDEN_TOP));
        for (RankedChunk chunk : itemTop) {
            section.append("**%d위 (%.4f)**\n\n```\n%s\n```\n\n".formatted(chunk.rank(), chunk.score(), chunk.text()));
        }
        return section.append("</details>\n").toString();
    }

    private static List<SourcedChunk> sourced(String label, List<String> chunks) {
        List<SourcedChunk> sourced = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            sourced.add(new SourcedChunk("%s %d위".formatted(label, i + 1), chunks.get(i)));
        }
        return sourced;
    }

    /** 인용문을 공백 없이 비교해 처음 포함하는 청크를 찾는다 — 못 찾으면 앞 15자로 한 번 더 찾는다. */
    private static SourcedChunk matchQuote(String quote, List<SourcedChunk> sources) {
        if (quote == null) {
            return null;
        }
        String needle = quote.replaceAll("\\s+", "");
        if (needle.length() < 4) {
            return null;
        }
        for (String candidate : List.of(needle, needle.substring(0, Math.min(15, needle.length())))) {
            for (SourcedChunk source : sources) {
                if (source.text().replaceAll("\\s+", "").contains(candidate)) {
                    return source;
                }
            }
        }
        return null;
    }

    private static String basisKind(String basis) {
        if (basis == null) {
            return "(미기재)";
        }
        return switch (basis) {
            case "CHUNK" -> "청크";
            case "PROMPT_RULE" -> "프롬프트 규칙";
            case "BOTH" -> "청크 + 프롬프트 규칙";
            default -> basis;
        };
    }

    private static String basisContent(BasisVerdict verdict, SourcedChunk used) {
        List<String> parts = new ArrayList<>();
        if (verdict.quote() != null) {
            String where = used == null ? "인용 불일치" : used.source();
            parts.add("[%s] “%s”".formatted(where, verdict.quote()));
        }
        if (verdict.rule() != null) {
            parts.add("[프롬프트] " + verdict.rule());
        }
        return parts.isEmpty() ? "(없음)" : String.join(" / ", parts);
    }

    private static String shortBasis(BasisVerdict verdict, SourcedChunk used) {
        String kind = basisKind(verdict.basis());
        if (used != null) {
            return kind + " · " + used.source();
        }
        return verdict.quote() != null ? kind + " · 인용 불일치" : kind;
    }

    private static String cell(String value) {
        return value.replaceAll("\\s+", " ").replace("|", "\\|").trim();
    }

    private static String truncate(String value, int max) {
        String flat = value.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    private void writeBasisReport(String header, StringBuilder summary, StringBuilder detail) throws IOException {
        Files.writeString(BASIS_REPORT, header + summary + "\n## 6. 물품별 상세\n" + detail);
    }

    /** 운영 generateVerdict와 같은 요청에 근거 필드만 더한 도구 스키마를 쓴다. */
    private BasisVerdict converseWithBasis(String regulations, ItemAnalysis item) {
        ConverseResponse response = bedrockRuntimeClient.converse(ConverseRequest.builder()
                .modelId(properties.modelId())
                .system(SystemContentBlock.fromText(BedrockQuarantineRegulationClient.SYSTEM_PROMPT))
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(client.userPrompt(regulations, item)))
                        .build())
                .toolConfig(basisToolConfig())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(properties.maxTokens())
                        .temperature(0.0f)
                        .build())
                .build());
        spentWon += costWon(response);

        ToolUseBlock toolUse = response.output().message().content().stream()
                .map(ContentBlock::toolUse)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("도구 사용 블록이 없다: " + response.stopReasonAsString()));
        Map<String, Document> fields = toolUse.input().asMap();
        RegulationLevel level = RegulationLevel.valueOf(fieldOf(fields, "level").trim().toUpperCase());
        QuarantineVerdict verdict = QuarantineVerdict.of(level, fieldOf(fields, "reason"),
                fieldOf(fields, "penalty"), fieldOf(fields, "action"));
        return new BasisVerdict(verdict.level(), verdict.reason(), verdict.penalty(), verdict.action(),
                fieldOf(fields, "basis"), blankToNull(fieldOf(fields, "basis_quote")),
                blankToNull(fieldOf(fields, "basis_rule")), response.usage().inputTokens());
    }

    private static String fieldOf(Map<String, Document> fields, String key) {
        Document value = fields.get(key);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ToolConfiguration basisToolConfig() {
        Document schema = Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", Document.mapBuilder()
                        .putDocument("level", Document.mapBuilder()
                                .putString("type", "string")
                                .putList("enum", List.of(Document.fromString("PROHIBITED"),
                                        Document.fromString("RESTRICTED"), Document.fromString("ALLOWED")))
                                .build())
                        .putDocument("reason", stringField("왜 걸렸는지(또는 왜 문제없는지) 1~2문장"))
                        .putDocument("penalty", stringField("발췌문에 과태료 금액이 있을 때만. 없거나 ALLOWED면 생략"))
                        .putDocument("action", stringField("PROHIBITED·RESTRICTED면 필수, ALLOWED면 생략"))
                        .putDocument("basis", Document.mapBuilder()
                                .putString("type", "string")
                                .putList("enum", List.of(Document.fromString("CHUNK"),
                                        Document.fromString("PROMPT_RULE"), Document.fromString("BOTH")))
                                .putString("description", "level을 정한 결정적 근거. 규정 발췌문의 내용이면 CHUNK, "
                                        + "시스템 프롬프트의 판정 규칙(분류 규칙, 검역증명서 규칙 등)이면 PROMPT_RULE, 둘 다면 BOTH")
                                .build())
                        .putDocument("basis_quote", stringField("basis가 CHUNK·BOTH면, level을 정한 핵심 문장을 규정 "
                                + "발췌문에서 고치지 말고 그대로 복사(연속된 40자 이내). 아니면 생략"))
                        .putDocument("basis_rule", stringField("basis가 PROMPT_RULE·BOTH면, 적용한 시스템 프롬프트 "
                                + "규칙을 한 줄로. 예) 근거 없음 → 분류 규칙: 일반 공산품은 ALLOWED. 아니면 생략"))
                        .build())
                .putList("required", List.of(Document.fromString("level"), Document.fromString("reason"),
                        Document.fromString("basis")))
                .build();

        return ToolConfiguration.builder()
                .tools(Tool.fromToolSpec(ToolSpecification.builder()
                        .name("report_quarantine_verdict")
                        .description("물품의 한국 반입 판정을 보고한다")
                        .inputSchema(ToolInputSchema.fromJson(schema))
                        .build()))
                .toolChoice(ToolChoice.fromTool(SpecificToolChoice.builder().name("report_quarantine_verdict").build()))
                .build();
    }

    private static Document stringField(String description) {
        return Document.mapBuilder()
                .putString("type", "string")
                .putString("description", description)
                .build();
    }

    /**
     * {@code GOLDEN_IDS}(예: {@code A6,A12,C16})가 있으면 그 물품만 돌린다 — 프롬프트를 고친 뒤 틀렸던
     * 물품과 회귀 확인용 물품만 다시 볼 때 쓴다. 결과 파일도 따로 남겨 전체 실행 결과를 덮어쓰지 않는다.
     */
    private List<GoldenCase> selectedGoldenCases() {
        String ids = System.getenv("GOLDEN_IDS");
        if (ids == null || ids.isBlank()) {
            return GOLDEN_SET;
        }
        Set<String> selected = Set.of(ids.replace(" ", "").split(","));
        return GOLDEN_SET.stream().filter(golden -> selected.contains(golden.id())).toList();
    }

    private Path goldenReport() {
        String ids = System.getenv("GOLDEN_IDS");
        return ids == null || ids.isBlank() ? GOLDEN_REPORT : Path.of("build", "quarantine-golden-set-subset.md");
    }

    private String goldenHeader(Map<String, Integer> currentOutcomes, Map<String, Integer> rerankOutcomes, int done,
            int total) {
        StringBuilder header = new StringBuilder("# 검역 골든셋 — 품목 질의만 리랭크 vs 현재\n\n")
                .append("- 판정 모델: `%s`\n".formatted(properties.modelId()))
                .append("- 현재: 품목·과태료 질의 모두 벡터 top-%d\n".formatted(properties.numberOfResults()))
                .append("- 변경안: 품목 질의 벡터 top-%d → Cohere Rerank 3.5 → 점수 %.2f 이상 중 top-%d / 과태료 질의는 현재와 같음\n"
                        .formatted(GOLDEN_POOL, GOLDEN_SCORE_THRESHOLD, GOLDEN_TOP))
                .append("- 진행: %d/%d종, 누적 비용 약 ₩%.0f\n\n".formatted(done, total, spentWon))
                .append("## 채점 요약\n\n| 결과 | 현재 | 변경안 |\n|---|---|---|\n");
        for (String outcome : List.of("일치", "놓침", "금지 약화", "과잉 판정", "등급 불일치", "채점 제외")) {
            header.append("| %s | %d | %d |\n".formatted(outcome,
                    currentOutcomes.getOrDefault(outcome, 0), rerankOutcomes.getOrDefault(outcome, 0)));
        }
        return header.append("\n## 물품별 결과\n\n").toString();
    }

    /** 물품 하나 분량 — 두 방식의 판정 문구와, 변경안에서 품목 질의 리랭크 top-5가 무엇이었는지. */
    private String goldenDetailOf(GoldenCase golden, String itemQuery, List<RankedChunk> itemTop, int penaltyChunkCount,
            QuarantineVerdict currentVerdict, QuarantineVerdict rerankVerdict, QuarantineVerdict fallbackVerdict) {
        StringBuilder section = new StringBuilder("""

                ## %s %s (기대=%s)

                **현재(top-%d)** — 판정=%s
                - reason: %s
                - penalty: %s
                - action: %s

                **변경안** — 판정=%s
                - reason: %s
                - penalty: %s
                - action: %s
                """.formatted(
                golden.id(), golden.item().name(), expectedLabel(golden.expected()),
                properties.numberOfResults(), currentVerdict.level().getLabel(),
                text(currentVerdict.reason()), text(currentVerdict.penalty()), text(currentVerdict.action()),
                rerankVerdict.level().getLabel(),
                text(rerankVerdict.reason()), text(rerankVerdict.penalty()), text(rerankVerdict.action())));

        if (fallbackVerdict != null) {
            section.append("""

                    **0개일 때 top-%d를 넣은 경우** — 판정=%s
                    - reason: %s
                    - penalty: %s
                    """.formatted(GOLDEN_FALLBACK_TOP, fallbackVerdict.level().getLabel(),
                    text(fallbackVerdict.reason()), text(fallbackVerdict.penalty())));
        }

        section.append("\n**품목 질의 리랭크 top-%d** — `%s`\n\n".formatted(GOLDEN_TOP, itemQuery))
                .append("| 순위 | 점수 | 채택(≥%.2f) | 과태료패턴 | 청크 앞부분 |\n|---|---|---|---|---|\n"
                        .formatted(GOLDEN_SCORE_THRESHOLD));
        for (RankedChunk chunk : itemTop) {
            section.append("| %d | %.4f | %s | %s | %s |\n".formatted(chunk.rank(), chunk.score(),
                    chunk.score() >= GOLDEN_SCORE_THRESHOLD ? "O" : "X",
                    chunk.hasPenaltyAmount() ? "O" : "X", preview(chunk.text())));
        }
        return section.append("\n과태료 질의 청크: %d개(리랭크 없음, 그대로 투입)\n".formatted(penaltyChunkCount))
                .toString();
    }

    private static String outcomeOf(RegulationLevel expected, RegulationLevel actual) {
        if (expected == null) {
            return "채점 제외";
        }
        if (expected == actual) {
            return "일치";
        }
        if (actual == RegulationLevel.ALLOWED) {
            return "놓침";
        }
        if (expected == RegulationLevel.PROHIBITED && actual == RegulationLevel.RESTRICTED) {
            return "금지 약화";
        }
        if (expected == RegulationLevel.ALLOWED) {
            return "과잉 판정";
        }
        return "등급 불일치";
    }

    private static String expectedLabel(RegulationLevel expected) {
        return expected == null ? "경계" : expected.getLabel();
    }

    private static String joinDistinct(List<String> first, List<String> second) {
        Set<String> chunks = new LinkedHashSet<>(first);
        chunks.addAll(second);
        return String.join("\n\n---\n\n", chunks);
    }

    /** 표 한 칸에 들어가도록 줄바꿈을 없애고 앞부분만 남긴다. */
    private static String preview(String chunk) {
        String flat = chunk.replaceAll("\\s+", " ").replace("|", "\\|").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "…";
    }

    private void writeGolden(String content) throws IOException {
        Path report = goldenReport();
        Files.createDirectories(report.getParent());
        Files.writeString(report, content);
    }

    /** Sonnet은 Haiku 단가의 3배라, 모델에 맞춰 곱해야 예산 제한이 제대로 걸린다. */
    private double costWon(ConverseResponse response) {
        double multiplier = properties.modelId().contains("sonnet") ? 3.0 : 1.0;
        return (response.usage().inputTokens() * INPUT_USD + response.usage().outputTokens() * OUTPUT_USD)
                * multiplier * WON_PER_USD;
    }

    private String topRegulationsOf(List<RankedChunk> itemRanked, List<RankedChunk> penaltyRanked) {
        Set<String> chunks = new LinkedHashSet<>();
        itemRanked.stream().limit(RERANK_TOP).map(RankedChunk::text).forEach(chunks::add);
        penaltyRanked.stream().limit(RERANK_TOP).map(RankedChunk::text).forEach(chunks::add);
        return String.join("\n\n---\n\n", chunks);
    }

    /** 판정 문구(현재 vs 리랭크)와 리랭크 순위·점수를 물품 하나 분량으로 묶는다. */
    private String detailSectionOf(ItemAnalysis item, QuarantineVerdict currentVerdict, QuarantineVerdict rerankVerdict,
            String itemQuery, List<RankedChunk> itemRanked, String penaltyQuery, List<RankedChunk> penaltyRanked) {
        return """

                ## %s

                **현재(top-%d)** — 판정=%s
                - reason: %s
                - penalty: %s
                - action: %s

                **리랭크(top-30→top-%d)** — 판정=%s
                - reason: %s
                - penalty: %s
                - action: %s

                %s
                %s
                """.formatted(
                item.name(),
                properties.numberOfResults(), currentVerdict.level().getLabel(),
                text(currentVerdict.reason()), text(currentVerdict.penalty()), text(currentVerdict.action()),
                RERANK_TOP, rerankVerdict.level().getLabel(),
                text(rerankVerdict.reason()), text(rerankVerdict.penalty()), text(rerankVerdict.action()),
                scoreDiagnostics("품목 질의: " + itemQuery, itemRanked),
                scoreDiagnostics("과태료 질의: " + penaltyQuery, penaltyRanked));
    }

    private static String text(String value) {
        return value == null ? "(없음)" : value;
    }

    /** top-10 순위·점수를 보여주고, 과태료 패턴을 가진 최상위 청크가 top-10 밖이면 그 순위·점수도 따로 알려준다. */
    private String scoreDiagnostics(String label, List<RankedChunk> ranked) {
        if (ranked.isEmpty()) {
            return "**" + label + "** — 검색 결과 없음\n";
        }
        int shown = Math.min(10, ranked.size());
        StringBuilder sb = new StringBuilder("**" + label + "**\n\n| 순위 | 점수 | 과태료패턴 |\n|---|---|---|\n");
        for (int i = 0; i < shown; i++) {
            RankedChunk chunk = ranked.get(i);
            sb.append("| %d | %.4f | %s |\n".formatted(chunk.rank(), chunk.score(), chunk.hasPenaltyAmount()));
        }
        List<RankedChunk> penaltyChunks = ranked.stream().filter(RankedChunk::hasPenaltyAmount).toList();
        if (penaltyChunks.isEmpty()) {
            sb.append("\n(전체 %d개 중 과태료패턴 포함 청크 없음)\n".formatted(ranked.size()));
        } else if (penaltyChunks.get(0).rank() > shown) {
            RankedChunk best = penaltyChunks.get(0);
            sb.append("\n(top-%d 밖) 과태료패턴 최상위 청크: %d위, 점수 %.4f\n"
                    .formatted(shown, best.rank(), best.score()));
        }
        return sb.toString();
    }

    private List<String> retrieveWide(String query) {
        return retrieve(query, WIDE_POOL);
    }

    private List<String> retrieve(String query, int numberOfResults) {
        return bedrockAgentRuntimeClient.retrieve(RetrieveRequest.builder()
                        .knowledgeBaseId(properties.knowledgeBaseId())
                        .retrievalQuery(KnowledgeBaseQuery.builder().text(query).build())
                        .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                                .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                        .numberOfResults(numberOfResults)
                                        .filter(importFilter())
                                        .build())
                                .build())
                        .build())
                .retrievalResults().stream()
                .map(result -> result.content().text())
                .toList();
    }

    /** 운영({@link BedrockQuarantineRegulationClient#importFilter}) 과 같은 필터 — scope=import 이면서 KR. */
    private RetrievalFilter importFilter() {
        return RetrievalFilter.fromAndAll(List.of(
                RetrievalFilter.fromEqualsValue(attribute("scope", "import")),
                RetrievalFilter.fromListContains(attribute("jurisdictions", "KR"))));
    }

    private FilterAttribute attribute(String key, String value) {
        return FilterAttribute.builder().key(key).value(Document.fromString(value)).build();
    }

    /** 순위 하나 — 점수는 Cohere가 매긴 relevanceScore, 순위는 1부터 시작한다. */
    private record RankedChunk(int rank, float score, String text) {
        boolean hasPenaltyAmount() {
            return QuarantineRerankVerdictManualIT.hasPenaltyAmount(text);
        }
    }

    /**
     * 풀 전체(최대 30개)를 순위·점수까지 매겨 돌려준다 — top-5만 자르면 "몇 등에서 잘렸는지"를
     * 알 수 없어서, 진단용으로는 풀 크기 그대로 리랭크한다. 리랭크 모델은 도쿄 리전에만 있어
     * 별도 클라이언트({@code tokyo})로 부른다.
     */
    private List<RankedChunk> rerankRanked(BedrockAgentRuntimeClient tokyo, String query, List<String> pool) {
        if (pool.isEmpty()) {
            return List.of();
        }
        List<RerankSource> sources = pool.stream()
                .map(text -> RerankSource.builder()
                        .type(RerankSourceType.INLINE)
                        .inlineDocumentSource(RerankDocument.builder()
                                .type(RerankDocumentType.TEXT)
                                .textDocument(RerankTextDocument.builder().text(text).build())
                                .build())
                        .build())
                .toList();

        List<RerankResult> results = tokyo.rerank(RerankRequest.builder()
                        .queries(RerankQuery.builder()
                                .type(RerankQueryContentType.TEXT)
                                .textQuery(RerankTextDocument.builder().text(query).build())
                                .build())
                        .sources(sources)
                        .rerankingConfiguration(RerankingConfiguration.builder()
                                .type(RerankingConfigurationType.BEDROCK_RERANKING_MODEL)
                                .bedrockRerankingConfiguration(BedrockRerankingConfiguration.builder()
                                        .numberOfResults(pool.size())
                                        .modelConfiguration(BedrockRerankingModelConfiguration.builder()
                                                .modelArn(COHERE_MODEL_ARN)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .results();

        List<RankedChunk> ranked = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            RerankResult result = results.get(i);
            ranked.add(new RankedChunk(i + 1, result.relevanceScore(), pool.get(result.index())));
        }
        return ranked;
    }

    /** 운영 generateVerdict와 같은 요청 — 시스템 프롬프트·유저 프롬프트·도구 스키마·temperature 0. */
    private ConverseResponse converse(String regulations, ItemAnalysis item) throws Exception {
        return bedrockRuntimeClient.converse(ConverseRequest.builder()
                .modelId(properties.modelId())
                .system(SystemContentBlock.fromText(BedrockQuarantineRegulationClient.SYSTEM_PROMPT))
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(client.userPrompt(regulations, item)))
                        .build())
                .toolConfig((ToolConfiguration) invokePrivate("verdictToolConfig"))
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(properties.maxTokens())
                        .temperature(0.0f)
                        .build())
                .build());
    }

    private QuarantineVerdict parseVerdict(ConverseResponse response) throws Exception {
        return (QuarantineVerdict) invokePrivate("parseVerdict", response);
    }

    /** 도구 설정·파싱은 private이라 리플렉션으로 부른다 — 운영 코드는 건드리지 않는다. */
    private Object invokePrivate(String name, Object... args) throws Exception {
        Method method = Arrays.stream(BedrockQuarantineRegulationClient.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == args.length)
                .findFirst()
                .orElseThrow();
        method.setAccessible(true);
        try {
            return method.invoke(client, args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    /** {@link BedrockQuarantineRegulationManualIT#retrievesPenaltyAmounts}와 같은 기준. */
    private static boolean hasPenaltyAmount(String regulations) {
        return regulations.matches("(?s).*(과태료|부과기준).*") && regulations.matches("(?s).*\\d.*만\\s?원.*");
    }

    private void write(String content) throws IOException {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, content);
    }
}
