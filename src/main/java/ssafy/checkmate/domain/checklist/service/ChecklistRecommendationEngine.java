package ssafy.checkmate.domain.checklist.service;

import java.time.temporal.ChronoUnit;
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
 * 15번(체크리스트 추천) 로직. 국가·항공편 여부·기간·날씨·목적을 프롬프트에 담아 GMS에게
 * 추천 목록을 받는다 — 규칙(국내/해외, 기간별 수량, 우산, 목적별 아이템)은 코드로 분기하지 않고
 * 프롬프트로 지시한다. 규칙을 바꾸려면 아래 SYSTEM_PROMPT 만 고치면 된다.
 *
 * ⚠️ 프롬프트 [5] 의 물품 목록은 <b>온디바이스 모델이 알아보는 클래스</b>와 같아야 한다
 *    (AI/integration/classes.json 의 "yolo" 배열, 2026-09-18 기준 23개).
 *    짐 싸기 인식은 이름이 같은 체크리스트 줄을 찾아 자동으로 체크하므로, 추천이 "세면도구" 처럼
 *    모델이 모르는 이름으로 오면 칫솔을 찍어도 짝이 안 맞아 새 줄이 생긴다.
 *    AI 팀이 클래스를 늘리거나 이름을 바꾸면 여기도 같이 고칠 것.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChecklistRecommendationEngine {

    private static final String SYSTEM_PROMPT = """
            당신은 여행 짐 싸기를 도와주는 어시스턴트입니다. 여행 정보와 날짜별 날씨 예보를 보고
            실제로 가방에 넣을 물건 목록을 추천하세요.

            [1] 국내 여행과 해외 여행을 반드시 구분하세요
            - 목적지 국가가 "대한민국"이면 **국내 여행**입니다. 항공편을 타더라도(제주 등) 국내입니다.
              · 여권, 비자, 여행용 멀티 어댑터, 환전한 외화, 해외용 유심/로밍은 **절대 넣지 마세요**.
              · 신분증, 교통카드처럼 국내에서 쓰는 것을 넣으세요.
            - 목적지 국가가 대한민국이 아니면 **해외 여행**입니다.
              · 여권은 반드시 넣으세요. 그 나라 콘센트 규격에 맞는 어댑터, 해외 결제 카드,
                유심/로밍, 상비약을 함께 고려하세요.
            - 항공편을 탄다면 기내 반입 제한을 고려하세요(액체류는 100ml 이하 용기).
              항공편을 타지 않는다면 기내 관련 물건(목베개, 기내 슬리퍼 등)은 넣지 마세요.

            [2] 기간에 맞는 수량을 넣으세요 — 수량은 "며칠"에서 나옵니다
            - 상의·속옷·양말: 여행 일수만큼. 단 7일을 넘으면 7개까지만 넣고 세탁을 전제하세요.
            - 하의: 2~3일에 1벌 (3박 4일이면 2벌).
            - 잠옷: 3일까지 1벌, 그보다 길면 2벌.
            - 충전기·치약처럼 하나면 되는 물건은 기간과 무관하게 1개입니다.

            [3] 날씨에 맞추세요
            - 예보 중 하루라도 "비" 또는 "눈"이거나 강수확률이 60% 이상인 날이 있으면 "우산"을 반드시 넣으세요.
            - 최고 기온이 28도 이상이면 선크림·모자를, 최저 기온이 5도 이하이면 패딩·목도리·장갑을 넣으세요.
            - 최저와 최고 기온 차가 10도 이상이면 겉옷(가디건·바람막이)을 넣으세요.
            - 실제 예보에 없는 날씨를 가정하지 마세요.

            [4] 여행 목적마다 물건을 더하세요
            - 예: 수영이면 수영복·물안경·비치타월, 등산이면 등산화·아이젠, 비즈니스면 정장·노트북.
            - 목적이 "미정"이면 목적 전용 물건은 넣지 마세요.

            [5] 아래 23개는 앱이 카메라로 알아볼 수 있는 물품입니다 — 이름을 여기에 맞추세요
            여권, 지갑, 노트북, 충전기, 보조배터리, 이어폰, 태블릿, 삼각대, 상의, 하의, 양말,
            장갑, 신발류, 모자, 선글라스, 백팩, 상비약, 칫솔, 치약, 우산, 가위, 칼, 텀블러

            - 추천할 물건이 이 목록의 것과 **같거나 사실상 같은 물건**이면, 목록에 적힌 이름을
              **글자 그대로** 쓰세요. 꾸미는 말을 붙이지 마세요.
              · "반팔 티셔츠", "긴팔 셔츠" → "상의"      · "청바지", "반바지" → "하의"
              · "운동화", "슬리퍼" → "신발류"            · "여행용 칫솔" → "칫솔"
              · "휴대폰 충전기", "케이블" → "충전기"     · "우비" → "우산"
            - 한 덩어리가 목록의 여러 물건에 해당하면 **쪼개서 각각** 넣으세요.
              · "세면도구" → "칫솔", "치약"   · "전자기기" → "노트북", "충전기", "보조배터리"
            - 목록에 없는 물건은 **원래 이름 그대로** 두세요. 억지로 목록에 끼워 맞추지 마세요.
              · "선크림", "수영복", "여행용 어댑터", "비치타월" 은 그대로 둡니다.
              · 수영복을 "상의"로, 노트북 파우치를 "노트북"으로 바꾸면 안 됩니다 — 다른 물건입니다.

            [6] 공통 규칙
            - 같은 물건을 두 번 넣지 마세요. 비슷한 물건도 하나로 합치세요("충전기"와 "휴대폰 충전기"는 하나).
            - 이름은 사람이 가방에 넣을 수 있는 구체적인 물건이어야 합니다. "필요한 서류" 같은 뭉뚱그린 이름은 안 됩니다.
            - 이름은 12자 이내의 한국어로 쓰세요.
            - 15~25개 사이로 추천하세요. 너무 적으면 빠진 것이 생기고, 너무 많으면 사용자가 지우느라 지칩니다.

            [7] category 는 반드시 다음 다섯 중 하나입니다 — 뜻을 지켜서 고르세요
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
              · 우산은 씻는 물건은 아니지만 일상에서 쓰는 물건이라 → "생활용품"

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

    /**
     * 모델에게 넘기는 여행 정보.
     *
     * 국가·항공편 여부·기간은 시스템 프롬프트의 [1]·[2] 규칙이 그대로 쓰는 값이라 반드시 넣는다
     * (2026-09-18). 예전에는 목적지·기간·목적·날씨만 넘겨서, 모델이 국내/해외를 알 수 없어
     * 부산 여행에도 여권을 추천했다.
     */
    private String buildUserPrompt(Trip trip, List<DailyWeather> forecast) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("목적지 국가: ").append(trip.getCountry()).append('\n');
        prompt.append("목적지 도시/지역: ").append(trip.getRegion()).append('\n');
        prompt.append("기간: ").append(trip.getStartDate()).append(" ~ ").append(trip.getEndDate());
        // 날짜가 비어 있으면(있어선 안 되지만) 박/일만 빼고 나머지는 그대로 보낸다 —
        // 여기서 터지면 recommend()의 catch가 삼켜서 체크리스트가 통째로 비어 버린다
        if (trip.getStartDate() != null && trip.getEndDate() != null) {
            long nights = Math.max(0, ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()));
            prompt.append(" (").append(nights).append("박 ").append(nights + 1).append("일)");
        }
        prompt.append('\n');
        prompt.append("항공편 이용: ").append(trip.isHasFlight() ? "예" : "아니오").append('\n');
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
