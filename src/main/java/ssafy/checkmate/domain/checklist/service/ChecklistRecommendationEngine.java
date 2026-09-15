package ssafy.checkmate.domain.checklist.service;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.weather.DailyWeather;
import tools.jackson.databind.ObjectMapper;

/**
 * 15번(체크리스트 추천) 로직. 날씨·여행 목적을 프롬프트에 담아 GMS에게 추천 목록을
 * 받는다 — 규칙(우산·목적별 아이템)은 코드로 분기하지 않고 프롬프트로 지시한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChecklistRecommendationEngine {

    private static final String SYSTEM_PROMPT = """
            당신은 여행 짐 싸기를 도와주는 어시스턴트입니다. 여행 정보와 날짜별 날씨 예보를 보고
            챙겨야 할 물건 목록을 추천하세요.

            규칙:
            1. 예보 중 하루라도 날씨가 "비" 또는 "눈"이면 반드시 "우산"을 목록에 포함하세요.
            2. 여행 목적에 어울리는 물건을 목적마다 추가하세요(예: 수영이면 수영복·물안경,
               캠핑이면 텐트·손전등).
            3. 여권처럼 목적·날씨와 무관하게 항상 필요한 물건도 포함하세요.
            4. 같은 물건을 중복해서 넣지 마세요.

            다른 설명이나 마크다운 코드블록 없이, 아래 형식의 JSON 배열만 응답하세요:
            [{"name": "물건 이름", "category": "카테고리", "quantity": 1}]
            """;

    private final GmsClient gmsClient;
    private final ObjectMapper objectMapper;

    public List<RecommendedItem> recommend(Trip trip, List<DailyWeather> forecast) {
        try {
            String content = gmsClient.chat(SYSTEM_PROMPT, buildUserPrompt(trip, forecast));
            return parse(content);
        } catch (Exception e) {
            log.warn("checklist recommendation via GMS failed, returning empty list", e);
            return List.of();
        }
    }

    private String buildUserPrompt(Trip trip, List<DailyWeather> forecast) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("목적지: ").append(trip.getDestination()).append('\n');
        prompt.append("기간: ").append(trip.getStartDate()).append(" ~ ").append(trip.getEndDate()).append('\n');
        prompt.append("여행 목적: ")
                .append(trip.getPurposes().isEmpty() ? "미정" : String.join(", ", trip.getPurposes()))
                .append('\n');
        prompt.append("날씨 예보:\n");
        if (forecast.isEmpty()) {
            prompt.append("- 예보 정보 없음\n");
        } else {
            for (DailyWeather day : forecast) {
                prompt.append("- ").append(day.getDate()).append(": ").append(day.getWeatherType().getLabel())
                        .append(", 최저 ").append(day.getMinTemperature()).append("도")
                        .append(", 최고 ").append(day.getMaxTemperature()).append("도")
                        .append(", 강수확률 ").append(day.getPrecipitationProbability()).append("%\n");
            }
        }
        return prompt.toString();
    }

    private List<RecommendedItem> parse(String content) {
        String json = stripCodeFence(content);
        RecommendedItemDto[] items = objectMapper.readValue(json, RecommendedItemDto[].class);
        return List.of(items).stream()
                .map(dto -> new RecommendedItem(dto.getName(), dto.getCategory(),
                        dto.getQuantity() == null ? 1 : dto.getQuantity()))
                .toList();
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
    static class RecommendedItemDto {
        private String name;
        private String category;
        private Integer quantity;
    }
}
