package ssafy.checkmate.domain.checklist.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * GMS의 Claude 비전 모델(Anthropic 경로, {@link GmsVisionClient})로 사진 속 물품을 읽는 구현(35번 1단계).
 *
 * 분류는 자유 서술이 아니라 고정 목록에서 고르게 한다 — 같은 물건이 어떤 날은 "육가공품", 어떤 날은
 * "고기 간식"으로 나오면 2단계 검색 질의가 흔들려 판정도 흔들린다. 목록을 바꾸면 명세(35번)의
 * 분류 표도 함께 고친다.
 *
 * Opus 4.7 이후 모델은 {@code temperature} 같은 샘플링 파라미터를 받지 않아(보내면 400) 보내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmsItemAnalysisClient implements ItemAnalysisClient {

    static final String SYSTEM_PROMPT = """
            당신은 여행자가 해외에서 산 물건의 사진을 보고, 한국 입국 시 세관·검역 판정에 필요한 정보를
            읽어 내는 전문가입니다.

            사진 속 물품 하나를 분석해 아래 JSON 객체 하나만 출력하세요. 설명·코드펜스·머리말은 쓰지 마세요.

            {"recognized": true, "name": "품목", "category": "분류", "ingredients": ["원재료"], "state": "상태"}

            규칙
            - recognized: 판정할 물품이 사진에 있으면 true, 사람·풍경·빈 화면처럼 물품이 없으면 false.
              false이면 나머지 필드는 빈 문자열/빈 배열로 두세요.
            - name: 품목. 반드시 한국어로, 구체적으로 적으세요. 외국어 라벨이 붙은 제품도 한국어로 옮기세요. (예: 육포)
            - category: 아래 분류 중 정확히 하나. 목록에 없으면 가장 가까운 분류를 고르되,
              검역 대상일 가능성이 있으면 더 엄격한 쪽을 고르세요.
              · 육류 가공품 (육포, 소시지, 햄, 베이컨)
              · 유제품 (치즈, 버터, 분유)
              · 수산물·수산가공품 (건어물, 김, 어묵)
              · 신선 과일·채소 (망고, 사과, 토마토)
              · 식물·종자·흙 (화분, 씨앗, 꽃, 흙)
              · 가공식품·과자 (라면, 과자, 초콜릿, 소스)
              · 주류·담배·향수 (위스키, 담배, 향수)
              · 의약품·건강기능식품 (영양제, 상비약)
              · 야생동식물 제품 (악어가죽, 산호, 상아, 거북 제품)
              · 문화재·골동품 (도자기, 고서화)
              · 일반 공산품 (의류, 인형, 전자제품, 화장품)
            - ingredients: 원재료. 포장 라벨이 보이면 라벨 기준으로, 안 보이면 물품에서 추정하세요.
              알 수 없으면 빈 배열. 한국어로 적으세요. (예: ["돼지고기", "소고기"])
            - state: 가공·보존 상태를 짧게. (예: 건조 가공, 냉동, 생것, 조리, 멸균 포장, 완제품)
            - 사진에 물품이 여럿이면 화면 중앙에 있거나 가장 큰 하나만 분석하세요.
            """;

    private static final String USER_TEXT = "이 사진 속 물품을 분석하세요.";

    private final GmsVisionClient gmsVisionClient;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<ItemAnalysis> analyze(ScanImage image) {
        long startedAt = System.nanoTime();
        String content = gmsVisionClient.ask(SYSTEM_PROMPT, USER_TEXT, image.mimeType(), image.bytes());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        Optional<ItemAnalysis> analysis = parse(content);
        if (analysis.isEmpty()) {
            log.info("[quarantine-check] 사진에서 물품을 찾지 못했다 소요={}ms", elapsedMillis);
            return analysis;
        }
        log.info("[quarantine-check] 물품 분석 완료 품목={} 분류={} 원재료={} 상태={} 소요={}ms",
                analysis.get().name(), analysis.get().category(), analysis.get().ingredients(),
                analysis.get().state(), elapsedMillis);
        return analysis;
    }

    private Optional<ItemAnalysis> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("비전 모델 응답이 비어 있습니다.");
        }
        AnalysisDto dto = objectMapper.readValue(GmsResponses.stripCodeFence(content), AnalysisDto.class);

        if (!Boolean.TRUE.equals(dto.getRecognized())) {
            return Optional.empty();
        }
        // 물품이 있다고 해 놓고 품목·분류가 비면 판정할 수 없다 — 인식 실패가 아니라 응답 오류다.
        if (isBlank(dto.getName()) || isBlank(dto.getCategory())) {
            log.error("[quarantine-check] 물품이 있다고 했지만 품목·분류가 비어 있다 raw={}", content);
            throw new IllegalStateException("비전 모델 응답에 품목 또는 분류가 없습니다.");
        }
        return Optional.of(new ItemAnalysis(
                dto.getName().trim(), dto.getCategory().trim(), dto.getIngredients(), textOrEmpty(dto.getState())));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String textOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class AnalysisDto {
        private Boolean recognized;
        private String name;
        private String category;
        private List<String> ingredients;
        private String state;
    }
}
