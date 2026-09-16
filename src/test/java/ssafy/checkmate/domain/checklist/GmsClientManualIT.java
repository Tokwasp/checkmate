package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.checkmate.domain.checklist.service.ChecklistRecommendationEngine;
import ssafy.checkmate.domain.checklist.service.GmsClient;
import ssafy.checkmate.domain.checklist.service.RecommendedItem;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.weather.DailyWeather;
import ssafy.checkmate.domain.weather.WeatherClient;
import ssafy.checkmate.domain.weather.WeatherType;

/**
 * 실제 GMS 게이트웨이를 호출하는 수동 확인용 테스트. mock을 전혀 쓰지 않는다.
 *
 * - {@code test} 프로필을 쓰지 않는다(일부러 @ActiveProfiles 없음) — application.yaml의
 *   ${GMS_BASE_URL}/${GMS_KEY}/${GMS_MODEL}가 로컬 .env 값으로 그대로 해석된다.
 * - 그래서 .env에 실제 값이 있어야 통과한다. CI/팀원 환경에서 실수로 돌면 곤란하니
 *   기본은 @Disabled로 꺼둔다 — 로컬에서 확인할 때만 이 줄을 지우고 실행한다.
 *
 * 실행: 아래 @Disabled를 지우고
 *   ./gradlew test --tests "ssafy.checkmate.domain.checklist.GmsClientManualIT"
 */
@Disabled("실제 GMS 호출 — 로컬에서 .env 값으로 수동 확인할 때만 주석 해제")
@SpringBootTest
class GmsClientManualIT {

    @Autowired
    private GmsClient gmsClient;

    @Autowired
    private ChecklistRecommendationEngine recommendationEngine;

    @Autowired
    private WeatherClient weatherClient;

    @Test
    void chatReturnsNonBlankContent() {
        String content = gmsClient.chat(
                "당신은 테스트 응답기입니다. 사용자가 무엇을 묻든 정확히 \"OK\"라고만 답하세요.",
                "연결 확인");

        System.out.println("[GMS raw response] " + content);
        assertThat(content).isNotBlank();
    }

    @Test
    void recommendReturnsItemsForRainyTrip() {
        Trip trip = Trip.builder()
                .memberId(1L)
                .country("일본")
                .region("오사카")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .purposes(List.of("수영", "캠핑"))
                .build();
        List<DailyWeather> forecast = List.of(
                new DailyWeather(trip.getStartDate(), WeatherType.RAINY, 14.0, 18.0, 80),
                new DailyWeather(trip.getStartDate().plusDays(1), WeatherType.SUNNY, 20.0, 27.0, 10));

        List<RecommendedItem> items = recommendationEngine.recommend(trip, forecast);

        System.out.println("[추천 결과]");
        items.forEach(item -> System.out.println(
                "- " + item.getName() + " (" + item.getCategory() + ") x" + item.getQuantity()));

        assertThat(items).isNotEmpty();
        assertThat(items).extracting(RecommendedItem::getName).anyMatch(name -> name.contains("우산"));
    }

    /**
     * 날씨도 목만들지 않고 실제 Open-Meteo 예보를 그대로 태워서, GMS까지 진짜로 돌아가는
     * 전체 파이프라인을 확인한다. 지오코딩은 더 이상 쓰지 않는다 — "대구"를 이름으로
     * 검색하면 북한 강원도 마을이 잡히는 문제(옛 docs/checklist/docs.md §9-2)가 있었고,
     * 이는 Google Places로 좌표를 미리 확보해 저장하는 방식으로 근본적으로 해결했다
     * (docs/travel/docs.md §9-7/§10). 그래서 이 테스트도 좌표를 직접 넣는다.
     */
    @Test
    void recommendForDaeguUsingRealWeather() {
        Trip trip = Trip.builder()
                .memberId(1L)
                .country("한국")
                .region("대구")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(4))
                .purposes(List.of("맛집 탐방"))
                .latitude(35.87028)
                .longitude(128.59111)
                .build();

        List<DailyWeather> forecast = weatherClient.getForecast(
                trip.getLatitude(), trip.getLongitude(), trip.getStartDate(), trip.getEndDate());

        System.out.println("[대구 날씨 예보 — Open-Meteo 실제 응답]");
        forecast.forEach(day -> System.out.println(
                "- " + day.getDate() + ": " + day.getWeatherType().getLabel()
                        + ", 최저 " + day.getMinTemperature() + "도, 최고 " + day.getMaxTemperature() + "도"
                        + ", 강수확률 " + day.getPrecipitationProbability() + "%"));

        List<RecommendedItem> items = recommendationEngine.recommend(trip, forecast);

        System.out.println("[대구 추천 결과 — GMS 실제 응답]");
        items.forEach(item -> System.out.println(
                "- " + item.getName() + " (" + item.getCategory() + ") x" + item.getQuantity()));

        // 지오코딩이 실패하면(§6-2 우려대로) forecast가 비어 있을 수 있다 — 그래도 추천
        // 자체는 실패하지 않아야 한다(목적 기반 추천은 날씨 없이도 나올 수 있음).
        assertThat(items).isNotEmpty();
    }
}
