package ssafy.checkmate.domain.checklist.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 17번(체크리스트 항목 일괄 추가)에서 카테고리를 보내지 않은 항목의 카테고리를 물품 이름으로 추론한다.
 *
 * 카테고리는 15번(추천)과 같은 다섯 가지로 맞춘다 — 인벤토리(23·24번)가 카테고리 문자열 그대로 묶기 때문에
 * 추론 결과가 추천 결과와 다른 이름이면 같은 종류의 물건이 서로 다른 탭으로 갈라진다.
 * 그래서 아래 SYSTEM_PROMPT 의 카테고리 정의는 {@link ChecklistRecommendationEngine} 의 [7] 과 같아야 한다.
 *
 * 추론은 부가 기능이라 실패해도 항목 추가를 막지 않는다 — GMS 호출·응답 파싱이 실패하거나
 * 다섯 가지 밖의 값이 오면 {@link #FALLBACK_CATEGORY} 로 대체한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChecklistCategoryInferrer {

    public static final String FALLBACK_CATEGORY = "기타";

    private static final Set<String> CATEGORIES = Set.of("의류", "전자기기", "생활용품", "서류", FALLBACK_CATEGORY);

    private static final String SYSTEM_PROMPT = """
            당신은 여행 짐 목록의 물건 이름을 보고 카테고리를 분류하는 어시스턴트입니다.

            category 는 반드시 다음 다섯 중 하나입니다 — 뜻을 지켜서 고르세요
            - "의류"     몸에 입거나 걸치는 것. 상의, 하의, 양말, 장갑, 신발류, 모자, 선글라스,
                        수영복, 잠옷, 속옷, 패딩, 겉옷
            - "전자기기"  전기로 동작하거나 그 부속. 노트북, 태블릿, 충전기, 보조배터리, 이어폰,
                        삼각대, 여행용 어댑터
            - "생활용품"  씻고 관리하고 일상에서 쓰는 것. 칫솔, 치약, 상비약, 우산, 텀블러, 가위, 칼,
                        선크림, 화장품, 수건, 세제
            - "서류"     신분·증빙. 여권, 비자, 신분증, 항공권, 지갑, 여행자보험 증서
            - "기타"     위 넷 어디에도 들어가지 않는 것만. 가방(백팩·캐리어), 우비 같은 것.

            - 헷갈리면 "기타"로 도망가지 말고 **가장 가까운 것**을 고르세요.
              · 상비약은 약이지 기기가 아닙니다 → "생활용품"
              · 수영복은 입는 것입니다 → "의류"
            - 이름의 뜻을 알 수 없어 정말 가늠이 안 될 때만 "기타"를 고르세요.

            사용자 메시지는 물건 이름의 JSON 배열입니다. 배열에 든 모든 이름에 대해, 이름은 **글자 그대로** 두고
            다른 설명이나 마크다운 코드블록 없이 아래 형식의 JSON 배열만 응답하세요:
            [{"name": "물건 이름", "category": "카테고리"}]
            """;

    private final GmsClient gmsClient;
    private final ObjectMapper objectMapper;

    /**
     * 이름 → 카테고리. 반환 맵의 키는 앞뒤 공백을 뗀 이름이고, 값은 항상 다섯 가지 중 하나다.
     * 추론하지 못한 이름은 맵에 없다 — 호출하는 쪽이 {@link #FALLBACK_CATEGORY} 로 대체한다.
     */
    public Map<String, String> infer(Collection<String> names) {
        List<String> distinctNames = names.stream().map(String::strip).distinct().toList();
        if (distinctNames.isEmpty()) {
            return Map.of();
        }
        try {
            String content = gmsClient.chat(SYSTEM_PROMPT, objectMapper.writeValueAsString(distinctNames));
            return parse(content);
        } catch (Exception e) {
            log.warn("category inference via GMS failed, falling back to {}", FALLBACK_CATEGORY, e);
            return Map.of();
        }
    }

    private Map<String, String> parse(String content) {
        InferredItemDto[] items = objectMapper.readValue(stripCodeFence(content), InferredItemDto[].class);
        Map<String, String> categoriesByName = new HashMap<>();
        for (InferredItemDto item : items) {
            if (item.getName() != null && CATEGORIES.contains(item.getCategory())) {
                categoriesByName.put(item.getName().strip(), item.getCategory());
            }
        }
        return categoriesByName;
    }

    private static String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\n?", "");
            trimmed = trimmed.replaceFirst("```\\s*$", "");
        }
        return trimmed.trim();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class InferredItemDto {
        private String name;
        private String category;
    }
}
