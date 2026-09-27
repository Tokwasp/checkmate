package ssafy.checkmate.domain.checklist.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.weather.DailyWeather;
import ssafy.checkmate.domain.weather.WeatherClient;

/**
 * 여행 목적만 바꿔 가며 체크리스트 추천을 여러 번 받아, 목적 전용 물품이 빠지는 응답이 있는지 본다.
 * 실제 GMS·Open-Meteo를 호출한다. 목적 전용 물품인지는 출력을 보고 사람이 판단한다.
 *
 * 목적은 앱의 기본 칩 6개(관광·비즈니스·힐링·골프·수영·등산)와 직접 입력으로 들어올 법한 4개다.
 * 여행지·기간·날씨는 모두 같게 둔다 — 호출마다 달라지는 건 목적과 모델 응답뿐이게 하려고.
 */
@Disabled("실호출·과금 테스트 — 자동으로 돌지 않게 막아 둔다. 돌려야 하면 이 줄을 지우고 로컬에서만 실행")
@SpringBootTest
class PurposeRecommendationManualIT {

    private static final int RUNS_PER_PURPOSE = 5;
    private static final List<String> PURPOSES = List.of(
            "관광", "비즈니스", "힐링", "골프", "수영", "등산",
            "낚시", "스쿠버다이빙", "자전거", "사진 촬영");

    @Autowired
    private ChecklistRecommendationEngine recommendationEngine;

    @Autowired
    private WeatherClient weatherClient;

    @Test
    void recommendForEachPurpose() {
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(4);
        double latitude = 26.2124;
        double longitude = 127.6809;
        List<DailyWeather> forecast = weatherClient.getForecast(latitude, longitude, startDate, endDate);

        System.out.println("[오키나와 날씨 예보]");
        forecast.forEach(day -> System.out.println("- " + day.getDate() + ": " + day.getWeatherType().getLabel()
                + ", 최저 " + day.getMinTemperature() + "도, 최고 " + day.getMaxTemperature() + "도"
                + ", 강수확률 " + day.getPrecipitationProbability() + "%"));

        for (String purpose : PURPOSES) {
            Trip trip = Trip.builder()
                    .memberId(1L)
                    .country("일본")
                    .region("오키나와")
                    .startDate(startDate)
                    .endDate(endDate)
                    .purposes(List.of(purpose))
                    .hasFlight(true)
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();
            for (int run = 1; run <= RUNS_PER_PURPOSE; run++) {
                List<RecommendedItem> items = recommendationEngine.recommend(trip, forecast);
                System.out.println("RESULT|" + purpose + "|" + run + "|" + items.stream()
                        .map(RecommendedItem::getName)
                        .collect(Collectors.joining(",")));
            }
        }
    }
}
