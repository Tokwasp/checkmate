package ssafy.checkmate.domain.checklist.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;

/**
 * 반입 규정 검사(30번)에서 <b>실제로 모델에 나가는 프롬프트를 그대로 파일로 떠보는</b> 확인용
 * 테스트. 프롬프트의 대부분이 유사도 검색으로 붙은 규정 청크라, 눈으로 봐야 검색이 제대로
 * 걸렸는지 알 수 있다.
 *
 * <p><b>검색(Retrieve)만 하고 생성(Converse)은 하지 않는다.</b> 임베딩 1회 + 벡터 쿼리라
 * 비용이 사실상 0이므로 마음껏 돌려도 된다 — 판정까지 받아보려면
 * {@code AviationRegulationClientManualIT}를 쓴다.
 *
 * <p>결과는 {@code build/prompt-dump/{국가}.txt}에 쓴다. 국가·물품을 바꿔보려면 아래
 * {@link #COUNTRIES}와 {@link #ITEMS}만 고치면 된다.
 *
 * <p>실행: 아래 @Disabled를 지우고
 * {@code ./gradlew test --tests "*SecurityCheckPromptDumpManualIT"}
 * (다른 ManualIT와 같이 {@code test} 프로필을 쓰지 않으므로 .env에 BEDROCK_KB_ID 등이 필요하다)
 */
@Disabled("실제 Bedrock 검색 호출 — 프롬프트를 확인할 때만 주석 해제")
@SpringBootTest
class SecurityCheckPromptDumpManualIT {

    /** 문서 언어가 서로 다른 세 곳 — 일본어 / 한국어 / 영어. */
    private static final List<CountryJurisdiction> COUNTRIES =
            List.of(CountryJurisdiction.JP, CountryJurisdiction.KR, CountryJurisdiction.US);

    /** 위험·조건부·안전이 섞이도록 고른 5종 (온디바이스 23종 중). */
    private static final List<SecurityCheckTarget> ITEMS = List.of(
            new SecurityCheckTarget(1L, "칼", "기내백", "기내"),
            new SecurityCheckTarget(2L, "가위", "기내백", "기내"),
            new SecurityCheckTarget(3L, "보조배터리", "캐리어", "위탁"),
            new SecurityCheckTarget(4L, "치약", "기내백", "기내"),
            new SecurityCheckTarget(5L, "여권", "기내백", "기내"));

    private static final Path OUT_DIR = Path.of("build", "prompt-dump");

    @Autowired
    private BedrockAviationRegulationClient client;

    @Test
    @DisplayName("국가별로 실제 나가는 프롬프트를 파일로 떠서 눈으로 확인한다")
    void dumpPrompts() throws IOException {
        Files.createDirectories(OUT_DIR);

        for (CountryJurisdiction jurisdiction : COUNTRIES) {
            String regulations = client.retrieveRegulations(jurisdiction, ITEMS);
            String userPrompt = client.userPrompt(regulations, ITEMS);
            String dump = render(jurisdiction, regulations, userPrompt);

            Path file = OUT_DIR.resolve(jurisdiction.name() + ".txt");
            Files.writeString(file, dump);

            System.out.printf("[prompt-dump] %s -> %s (%,d자, 청크 %d개)%n",
                    jurisdiction, file.toAbsolutePath(), dump.length(), chunkCountOf(regulations));
            assertThat(regulations).isNotBlank();
        }

        System.out.println("[prompt-dump] 파일을 열어 검색된 규정이 물품과 맞는지 확인할 것: "
                + OUT_DIR.toAbsolutePath());
    }

    /** 시스템 프롬프트 + 사용자 프롬프트를 실제 전송 순서대로 이어 붙인다. */
    private String render(CountryJurisdiction jurisdiction, String regulations, String userPrompt) {
        return """
                ================ %s (필터: jurisdictions IN %s, scope=avsec) ================
                검색 청크 %d개 · 규정 발췌문 %,d자 · 사용자 프롬프트 %,d자

                ---------------- SYSTEM ----------------
                %s
                ---------------- USER ------------------
                %s
                """.formatted(jurisdiction, jurisdiction.filterCodes(), chunkCountOf(regulations),
                regulations.length(), userPrompt.length(),
                BedrockAviationRegulationClient.SYSTEM_PROMPT, userPrompt);
    }

    /** 청크는 구분선으로 이어 붙이므로 구분선 수 + 1이 청크 수다. */
    private int chunkCountOf(String regulations) {
        return regulations.isBlank() ? 0 : regulations.split("\n\n---\n\n", -1).length;
    }
}
