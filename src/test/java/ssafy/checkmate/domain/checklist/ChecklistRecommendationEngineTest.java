package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.checkmate.domain.checklist.service.ChecklistRecommendationEngine;
import ssafy.checkmate.domain.checklist.service.GmsClient;
import ssafy.checkmate.domain.checklist.service.RecommendedItem;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.weather.DailyWeather;
import ssafy.checkmate.domain.weather.WeatherType;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ChecklistRecommendationEngineTest {

    @Mock
    private GmsClient gmsClient;

    @Test
    @DisplayName("GMS 응답 JSON을 추천 항목 목록으로 변환한다")
    void recommend() {
        // given
        given(gmsClient.chat(anyString(), anyString())).willReturn("""
                [{"name": "우산", "category": "생활용품", "quantity": 1},
                 {"name": "수영복", "category": "의류", "quantity": 1}]
                """);
        Trip trip = trip(List.of("수영"));

        // when
        List<RecommendedItem> items = engine().recommend(trip, List.of(rainyDay()));

        // then
        assertThat(items).hasSize(2)
                .extracting(RecommendedItem::getName)
                .containsExactly("우산", "수영복");
    }

    @Test
    @DisplayName("마크다운 코드블록으로 감싸서 응답해도 JSON만 파싱한다")
    void recommendWithCodeFence() {
        // given
        given(gmsClient.chat(anyString(), anyString())).willReturn("""
                ```json
                [{"name": "여권", "category": "서류", "quantity": 1}]
                ```
                """);

        // when
        List<RecommendedItem> items = engine().recommend(trip(List.of()), List.of());

        // then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getName()).isEqualTo("여권");
    }

    @Test
    @DisplayName("quantity가 없으면 기본값 1을 쓴다")
    void recommendWithoutQuantity() {
        // given
        given(gmsClient.chat(anyString(), anyString()))
                .willReturn("[{\"name\": \"여권\", \"category\": \"서류\"}]");

        // when
        List<RecommendedItem> items = engine().recommend(trip(List.of()), List.of());

        // then
        assertThat(items.get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("GMS 호출이 실패하면 빈 목록을 반환한다")
    void recommendWhenGmsFails() {
        // given
        given(gmsClient.chat(anyString(), anyString())).willThrow(new RuntimeException("gms down"));

        // when
        List<RecommendedItem> items = engine().recommend(trip(List.of()), List.of());

        // then
        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("응답이 JSON 형식이 아니면 빈 목록을 반환한다")
    void recommendWithInvalidJson() {
        // given
        given(gmsClient.chat(anyString(), anyString())).willReturn("이것은 JSON이 아닙니다");

        // when
        List<RecommendedItem> items = engine().recommend(trip(List.of()), List.of());

        // then
        assertThat(items).isEmpty();
    }

    private ChecklistRecommendationEngine engine() {
        return new ChecklistRecommendationEngine(gmsClient, new ObjectMapper());
    }

    private Trip trip(List<String> purposes) {
        return Trip.builder()
                .memberId(1L)
                .country("일본")
                .region("오사카")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2026, 4, 3))
                .purposes(purposes)
                .build();
    }

    private DailyWeather rainyDay() {
        return new DailyWeather(LocalDate.of(2026, 4, 1), WeatherType.RAINY, 14.0, 18.0, 80);
    }
}
