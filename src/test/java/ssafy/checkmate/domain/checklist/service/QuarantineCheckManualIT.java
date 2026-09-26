package ssafy.checkmate.domain.checklist.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import ssafy.checkmate.domain.checklist.dto.request.QuarantineCheckRequest;
import ssafy.checkmate.domain.checklist.dto.response.QuarantineCheckResponse;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.repository.MemberRepository;

/**
 * 촬영 물품 반입 판정(35번)을 <b>실제로 호출</b>하는 수동 확인용 테스트. mock을 전혀 쓰지 않는다.
 *
 * - {@code test} 프로필을 쓰지 않는다(일부러 @ActiveProfiles 없음) — application.yaml의
 *   ${GMS_BASE_URL}/${GMS_KEY}/${GMS_VISION_MODEL} 등이 로컬 .env 값으로 그대로 해석된다.
 * - 호출할 때마다 실제로 과금된다(Opus 비전 호출은 사진 한 장에 수십 원대). 그래서 기존 수동 IT처럼
 *   {@code @Disabled}로 꺼두는 대신, <b>환경변수를 준 실행에서만</b> 돈다 — CI/팀원 환경에서 실수로
 *   돌 일이 없고, 켤 때 파일을 고칠 필요도 없다.
 *
 * 실행 (사진이 든 폴더를 준다. jpg/png/webp 전부 호출하므로 장수만큼 과금된다)
 * <pre>
 *   QUARANTINE_IMAGE_DIR=C:/photos ./gradlew test --tests "*QuarantineCheckManualIT"
 * </pre>
 * Bedrock까지 포함한 전체 흐름은 AWS 자격증명과 {@code BEDROCK_KB_ID}가 있는 환경에서
 * {@code QUARANTINE_BEDROCK=true}를 함께 준다.
 *
 * 결과는 표준 출력에 {@code [MANUAL]} 접두사로 찍힌다(build/test-results의 XML 또는 IDE 콘솔).
 */
@Disabled("실호출·과금 테스트 — 자동으로 돌지 않게 막아 둔다. 돌려야 하면 이 줄을 지우고 로컬에서만 실행")
@SpringBootTest
class QuarantineCheckManualIT {

    private static final String IMAGE_DIR_ENV = "QUARANTINE_IMAGE_DIR";

    @Autowired
    private GmsVisionClient gmsVisionClient;

    @Autowired
    private ItemAnalysisClient itemAnalysisClient;

    @Autowired
    private QuarantineCheckService quarantineCheckService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("첫 번째 사진의 GMS 원본 응답 — JSON만 오는지, 코드펜스·설명이 섞이는지 확인한다")
    void rawGmsResponse() throws IOException {
        Path first = images().get(0);
        ScanImage image = scanImageOf(first);

        long startedAt = System.nanoTime();
        String raw = gmsVisionClient.ask(GmsItemAnalysisClient.SYSTEM_PROMPT, "이 사진 속 물품을 분석하세요.",
                image.mimeType(), image.bytes());

        System.out.printf("[MANUAL] 원본 응답 file=%s 소요=%dms%n%s%n[MANUAL] --- 끝 ---%n",
                first.getFileName(), elapsedMillisSince(startedAt), raw);
        assertThat(raw).isNotBlank();
    }

    @Test
    @DisplayName("폴더의 사진마다 물품 분석(1단계)을 실제로 호출해 품목·분류·원재료·상태를 확인한다")
    void analyzeImages() throws IOException {
        for (Path path : images()) {
            long startedAt = System.nanoTime();
            Optional<ItemAnalysis> analysis = itemAnalysisClient.analyze(scanImageOf(path));

            System.out.printf("[MANUAL] 분석 file=%s 소요=%dms → %s%n",
                    path.getFileName(), elapsedMillisSince(startedAt),
                    analysis.map(Object::toString).orElse("(물품 없음 → 서비스라면 422)"));
            analysis.ifPresent(item -> assertThat(item.category()).isNotBlank());
        }
    }

    @Test
    @DisplayName("폴더의 사진마다 전체 흐름(분석 → 규정 검색 → 판정)을 실제로 호출한다")
    void checkEndToEnd() throws IOException {
        Long memberId = memberRepository.save(Member.builder()
                .email("manual-it@example.com")
                .nickname("manual")
                .profileImageUrl("https://example.com/p.png")
                .provider(Provider.GOOGLE)
                .build()).getId();

        for (Path path : images()) {
            QuarantineCheckRequest request = new QuarantineCheckRequest();
            request.setImage(new MockMultipartFile("image", path.getFileName().toString(),
                    Files.probeContentType(path), Files.readAllBytes(path)));

            long startedAt = System.nanoTime();
            try {
                QuarantineCheckResponse response = quarantineCheckService.check(memberId, request);
                System.out.printf("[MANUAL] 판정 file=%s 소요=%dms%n  품목=%s 분류=%s 원재료=%s 상태=%s%n"
                                + "  판정=%s%n  사유=%s%n  과태료=%s%n  조치=%s%n",
                        path.getFileName(), elapsedMillisSince(startedAt),
                        response.getItem().getName(), response.getItem().getCategory(),
                        response.getItem().getIngredients(), response.getItem().getState(),
                        response.getVerdict().getLevel().getLabel(), response.getVerdict().getReason(),
                        response.getVerdict().getPenalty(), response.getVerdict().getAction());
            } catch (RuntimeException e) {
                System.out.printf("[MANUAL] 판정 file=%s 소요=%dms → %s: %s%n",
                        path.getFileName(), elapsedMillisSince(startedAt),
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private List<Path> images() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getenv(IMAGE_DIR_ENV)))) {
            List<Path> images = files
                    .filter(path -> path.getFileName().toString().matches("(?i).+\\.(jpe?g|png|webp)"))
                    .sorted()
                    .toList();
            assertThat(images).as("%s 폴더에 jpg/png/webp 사진이 있어야 한다", IMAGE_DIR_ENV).isNotEmpty();
            return images;
        }
    }

    private ScanImage scanImageOf(Path path) throws IOException {
        return ScanImage.from(new MockMultipartFile("image", path.getFileName().toString(),
                Files.probeContentType(path), Files.readAllBytes(path)));
    }

    private static long elapsedMillisSince(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
