package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.domain.checklist.service.AviationRegulationClient;
import ssafy.checkmate.domain.checklist.service.SecurityCheckTarget;
import ssafy.checkmate.domain.checklist.service.SecurityVerdict;
import ssafy.checkmate.domain.checklist.service.VerdictLevel;

/**
 * 실제 Bedrock Knowledge Base를 호출하는 수동 확인용 테스트. mock을 전혀 쓰지 않는다.
 *
 * - {@code test} 프로필을 쓰지 않는다(일부러 @ActiveProfiles 없음) — application.yaml의
 *   ${BEDROCK_KB_ID} 등이 로컬 .env / AWS 자격증명으로 그대로 해석된다.
 * - 호출할 때마다 실제로 과금된다(물품 몇 개 기준 회당 수십 원). CI/팀원 환경에서 실수로
 *   돌면 곤란하니 기본은 @Disabled로 꺼둔다 — 로컬에서 확인할 때만 이 줄을 지운다.
 *
 * 실행: 아래 @Disabled를 지우고
 *   ./gradlew test --tests "ssafy.checkmate.domain.checklist.AviationRegulationClientManualIT"
 */
@Disabled("실제 Bedrock 호출 — 로컬에서 수동 확인할 때만 주석 해제")
@SpringBootTest
class AviationRegulationClientManualIT {

    @Autowired
    private AviationRegulationClient aviationRegulationClient;

    @Test
    @DisplayName("기내 가방의 맥가이버칼은 금지로 판정되고, 조치 방법이 담긴 안내 문구가 온다")
    void judgesKnifeInCabinBagAsViolation() {
        List<SecurityVerdict> verdicts = aviationRegulationClient.judge(
                CountryJurisdiction.JP,
                List.of(new SecurityCheckTarget(1L, "맥가이버칼", "기내백", "기내")));

        printVerdicts(verdicts);
        assertThat(verdicts).hasSize(1);
        assertThat(verdicts.get(0).getItemId()).isEqualTo(1L);
        assertThat(verdicts.get(0).getLevel()).isEqualTo(VerdictLevel.VIOLATION);
        assertThat(verdicts.get(0).getGuide()).isNotBlank();
    }

    @Test
    @DisplayName("위탁 가방의 보조배터리는 금지로 판정된다 — GLOBAL(IATA) 문서가 필터에 포함되는지 확인")
    void judgesPowerBankInCheckedBagAsViolation() {
        List<SecurityVerdict> verdicts = aviationRegulationClient.judge(
                CountryJurisdiction.JP,
                List.of(new SecurityCheckTarget(2L, "보조배터리", "캐리어", "위탁")));

        printVerdicts(verdicts);
        assertThat(verdicts).hasSize(1);
        assertThat(verdicts.get(0).getLevel()).isEqualTo(VerdictLevel.VIOLATION);
    }

    @Test
    @DisplayName("여러 묶음을 한 번에 물어도 보낸 itemId를 그대로 돌려주고 통과 물품도 빠뜨리지 않는다")
    void judgesMultipleTargetsAndEchoesEveryItemId() {
        List<SecurityCheckTarget> targets = List.of(
                new SecurityCheckTarget(11L, "맥가이버칼", "기내백", "기내"),
                new SecurityCheckTarget(12L, "보조배터리", "캐리어", "위탁"),
                new SecurityCheckTarget(13L, "화장품", "기내백", "기내"),
                new SecurityCheckTarget(14L, "여권", "기내백", "기내"),
                new SecurityCheckTarget(15L, "양말", "캐리어", "위탁"));

        List<SecurityVerdict> verdicts = aviationRegulationClient.judge(CountryJurisdiction.JP, targets);

        printVerdicts(verdicts);
        assertThat(verdicts).extracting(SecurityVerdict::getItemId)
                .containsExactlyInAnyOrder(11L, 12L, 13L, 14L, 15L);
        assertThat(verdicts).filteredOn(verdict -> verdict.getLevel() == VerdictLevel.NONE)
                .allSatisfy(verdict -> assertThat(verdict.getGuide()).isNull());

        // 여러 물품을 한 번에 물어도 단독으로 물었을 때와 판정이 같아야 한다.
        // 물품명을 이어 붙여 한 번만 검색하면 쿼리가 희석돼 칼 관련 규정이 상위 결과에서
        // 밀려나고, 금지 물품이 통과로 나온다 — 실제로 재현된 버그라 회귀를 여기서 막는다.
        assertThat(levelOf(verdicts, 11L)).isEqualTo(VerdictLevel.VIOLATION);
        assertThat(levelOf(verdicts, 12L)).isEqualTo(VerdictLevel.VIOLATION);
        assertThat(levelOf(verdicts, 13L)).isEqualTo(VerdictLevel.WARNING);
    }

    @Test
    @DisplayName("기내·위탁이 미지정인 가방도 판정이 돌아온다")
    void judgesUnspecifiedCarriageType() {
        List<SecurityVerdict> verdicts = aviationRegulationClient.judge(
                CountryJurisdiction.KR,
                List.of(new SecurityCheckTarget(21L, "맥가이버칼", "내 캐리어", SecurityCheckTarget.UNSPECIFIED)));

        printVerdicts(verdicts);
        assertThat(verdicts).hasSize(1);
    }

    private VerdictLevel levelOf(List<SecurityVerdict> verdicts, Long itemId) {
        return verdicts.stream()
                .filter(verdict -> verdict.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("판정에 itemId=" + itemId + "이 없다"))
                .getLevel();
    }

    /** 문구 품질(정제 여부·길이·말투)은 눈으로 확인해야 해서 그대로 찍는다. */
    private void printVerdicts(List<SecurityVerdict> verdicts) {
        verdicts.forEach(verdict -> System.out.printf(
                "[verdict] itemId=%d level=%s guide=%s%n",
                verdict.getItemId(), verdict.getLevel(), verdict.getGuide()));
    }
}
