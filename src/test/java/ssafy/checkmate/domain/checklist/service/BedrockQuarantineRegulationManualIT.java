package ssafy.checkmate.domain.checklist.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;

/**
 * 촬영 물품 반입 판정(35번)의 <b>2·3단계만</b> 실제 Bedrock으로 호출하는 수동 확인용 테스트.
 * mock을 쓰지 않는다.
 *
 * 1단계(GMS Opus 비전)는 건너뛴다 — 사진 대신 {@link ItemAnalysis}를 직접 만들어 넣는다. 그래서
 * {@code GMS_VISION_MODEL}이 없어도 돌고, 비싼 Opus 호출 없이 <b>지식베이스 필터·검색 질의·판정</b>만
 * 따로 확인할 수 있다. 사진부터 끝까지 확인하려면 {@link QuarantineCheckManualIT}를 쓴다.
 *
 * - {@code test} 프로필을 쓰지 않는다(일부러 @ActiveProfiles 없음) — application.yaml의
 *   ${BEDROCK_KB_ID} 등이 로컬 .env / AWS 자격증명으로 그대로 해석된다.
 * - 호출할 때마다 과금된다(검색 2회 + 판정 생성 1회 x 케이스 수). 실수로 돌지 않도록 클래스에
 *   {@code @Disabled}를 걸어 두었다 — 돌려야 하면 로컬에서 그 줄을 지우고 필요한 메서드만 실행한다.
 *
 * 결과는 표준 출력에 {@code [MANUAL]} 접두사로 찍힌다(build/test-results의 XML 또는 IDE 콘솔).
 */
@Disabled("실호출·과금 테스트 — 자동으로 돌지 않게 막아 둔다. 돌려야 하면 이 줄을 지우고 로컬에서만 실행")
@SpringBootTest
class BedrockQuarantineRegulationManualIT {

    private static final ItemAnalysis PORK_JERKY =
            new ItemAnalysis("육포", "육류 가공품", List.of("돼지고기", "소고기"), "건조 가공");
    private static final ItemAnalysis MANGO =
            new ItemAnalysis("생망고", "신선 과일·채소", List.of("망고"), "생것");
    private static final ItemAnalysis WHISKY =
            new ItemAnalysis("위스키", "주류·담배·향수", List.of("보리", "알코올"), "병입 완제품");
    private static final ItemAnalysis PLUSH_DOLL =
            new ItemAnalysis("캐릭터 인형", "일반 공산품", List.of("폴리에스터", "솜"), "완제품");

    @Autowired
    private QuarantineRegulationClient quarantineRegulationClient;

    @Autowired
    private BedrockQuarantineRegulationClient bedrockClient;

    @Test
    @DisplayName("지식베이스 필터(scope=import · KR)가 실제로 청크를 돌려준다 — 필터가 너무 좁으면 여기서 빈다")
    void retrievesKoreanImportRegulations() {
        String regulations = bedrockClient.retrieveRegulations(PORK_JERKY);

        System.out.printf("[MANUAL] 검색 질의 ① %s%n[MANUAL] 검색 질의 ② %s%n"
                        + "[MANUAL] 발췌문 길이=%d자%n%s%n[MANUAL] --- 끝 ---%n",
                BedrockQuarantineRegulationClient.itemQuery(PORK_JERKY),
                BedrockQuarantineRegulationClient.penaltyQuery(PORK_JERKY),
                regulations.length(), regulations);
        assertThat(regulations).as("scope=import · KR 필터로 한 건도 못 찾으면 판정이 프롬프트 보수 규칙에만 기댄다")
                .isNotBlank();
    }

    /**
     * 명세의 "확인이 필요한 것" 8번 — 질의 ②가 실제로 과태료 <b>부과기준 문서</b>(가축전염병 예방법
     * 시행령 별표 3)를 끌어오는지. 안 끌어오면 판정문에서 {@code penalty}가 통째로 빠진다.
     */
    @Test
    @DisplayName("과태료 질의(②)가 금액이 적힌 부과기준 문서를 끌어온다 — 못 끌어오면 penalty가 빈다")
    void retrievesPenaltyAmounts() {
        String regulations = bedrockClient.retrieveRegulations(PORK_JERKY);

        boolean hasAmount = regulations.matches("(?s).*(과태료|부과기준).*")
                && regulations.matches("(?s).*\\d.*만\\s?원.*");
        System.out.printf("[MANUAL] 과태료 문구 포함=%s · 금액 표기 포함=%s%n",
                regulations.contains("과태료"), hasAmount);
        assertThat(hasAmount).as("발췌문에 금액이 없으면 프롬프트가 penalty를 비우도록 지시하므로 과태료 안내가 나가지 않는다")
                .isTrue();
    }

    /**
     * 명세(35번)가 응답 예시로 못 박아 둔 기준 케이스다 — {@code 금지} · 과태료 {@code 100만~1,000만 원} ·
     * 조치에 "자진 신고하면 과태료 없이 압수·폐기로 끝나요".
     *
     * <b>2026-09-21 실측에서 세 항목 모두 명세와 달랐다.</b> 판정은 {@code 제한}, 과태료는
     * {@code 100만~500만 원}, 조치에는 자진신고 문구 대신 "검역증명서가 있으면 과태료 없이 통과할 수
     * 있어요"가 왔다. 검색된 발췌문에는 ASF {@code 500/750/1,000}행과 자진신고 근거가 <b>모두 들어
     * 있었으므로</b> 검색이 아니라 프롬프트·생성 쪽 문제다. 세 항목을 한 번의 호출로 함께 보기 위해
     * soft assertion을 쓴다.
     */
    @Test
    @DisplayName("돼지고기 육포는 금지로 판정되고 ASF 금액 범위와 자진신고 안내가 함께 온다")
    void judgesPorkJerkyAsProhibited() {
        QuarantineVerdict verdict = judgeAndPrint(PORK_JERKY);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(verdict.reason()).isNotBlank();
            softly.assertThat(verdict.level())
                    .as("여행자가 든 육포에 검역증명서가 있을 리 없다 — 조건부로 보면 금지가 제한으로 새어 나간다")
                    .isEqualTo(RegulationLevel.PROHIBITED);
            softly.assertThat(verdict.penalty())
                    .as("원재료에 돼지고기가 있으면 ASF 발생국산일 수 있어 상한이 1,000만 원이다")
                    .isNotNull()
                    .contains("1,000만");
            softly.assertThat(verdict.action())
                    .as("발췌문에 자진신고 근거가 있으면 과태료 없이 압수·폐기로 끝난다고 알려 줘야 한다")
                    .isNotNull()
                    .contains("자진 신고");
        });
    }

    @Test
    @DisplayName("생망고는 허용으로 새지 않는다 — 검역 대상 분류는 최소한 제한이어야 한다")
    void judgesFreshMangoAsNotAllowed() {
        QuarantineVerdict verdict = judgeAndPrint(MANGO);

        assertThat(verdict.level()).isNotEqualTo(RegulationLevel.ALLOWED);
        assertThat(verdict.action()).isNotBlank();
    }

    @Test
    @DisplayName("위스키는 면세 한도가 있어 제한으로 판정된다 — 조건부 물품을 금지로 과잉 판정하지 않는다")
    void judgesWhiskyAsRestricted() {
        QuarantineVerdict verdict = judgeAndPrint(WHISKY);

        assertThat(verdict.level()).isEqualTo(RegulationLevel.RESTRICTED);
        assertThat(verdict.action()).isNotBlank();
    }

    @Test
    @DisplayName("일반 공산품은 허용으로 판정되고 과태료·조치가 붙지 않는다")
    void judgesPlushDollAsAllowed() {
        QuarantineVerdict verdict = judgeAndPrint(PLUSH_DOLL);

        assertThat(verdict.level()).isEqualTo(RegulationLevel.ALLOWED);
        assertThat(verdict.penalty()).isNull();
        assertThat(verdict.action()).isNull();
    }

    /**
     * 같은 물품을 두 번 판정했는데 단계가 달라지면 안전 기능으로서 신뢰를 잃는다
     * ({@code temperature=0}으로 잡아 둔 이유). 문구까지 토씨 하나 같기를 요구하지는 않는다.
     */
    @Test
    @DisplayName("같은 물품을 두 번 판정해도 단계가 바뀌지 않는다")
    void judgesSameItemConsistently() {
        QuarantineVerdict first = judgeAndPrint(PORK_JERKY);
        QuarantineVerdict second = judgeAndPrint(PORK_JERKY);

        assertThat(second.level()).isEqualTo(first.level());
    }

    /**
     * 운영 경로(품목 질의 리랭크 포함)로 한 건만 판정한다 — 리랭크 설정·권한이 실제로 동작하는지 확인하는 용도.
     * 상아는 벡터 검색만으로는 상아 규정이 11위라 top-5에 없고, 리랭크 뒤 1위가 되는 물품이다.
     * 로그에 "품목 질의 리랭크 후보=…건 채택=…건"이 찍히면 리랭크가 돈 것이고, "리랭크 실패"가 찍히면
     * 권한·모델 접근을 확인해야 한다.
     */
    @Test
    @DisplayName("운영 경로(품목 질의 리랭크)로 상아 조각품 한 건을 판정한다")
    void judgesOneItemWithRerank() {
        ItemAnalysis ivory = new ItemAnalysis("상아 조각품", "야생동식물 제품", List.of("상아"), "완제품");

        QuarantineVerdict verdict = judgeAndPrint(ivory);

        assertThat(verdict.level()).isEqualTo(RegulationLevel.PROHIBITED);
    }

    private QuarantineVerdict judgeAndPrint(ItemAnalysis item) {
        long startedAt = System.nanoTime();
        QuarantineVerdict verdict = quarantineRegulationClient.judge(item);

        System.out.printf("[MANUAL] 판정 품목=%s 분류=%s 소요=%dms%n"
                        + "  판정=%s%n  사유=%s%n  과태료=%s%n  조치=%s%n",
                item.name(), item.category(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                verdict.level().getLabel(), verdict.reason(), verdict.penalty(), verdict.action());
        return verdict;
    }
}
