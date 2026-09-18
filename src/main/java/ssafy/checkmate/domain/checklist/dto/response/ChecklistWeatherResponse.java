package ssafy.checkmate.domain.checklist.dto.response;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.Getter;
import ssafy.checkmate.domain.weather.DailyWeather;
import ssafy.checkmate.domain.weather.WeatherType;

@Getter
public class ChecklistWeatherResponse {

    private static final String SUNNY = "맑음";
    private static final String RAINY = "비";
    private static final String SNOWY = "눈";

    private final int totalDays;
    private final String precipitationType;
    private final int precipitationDays;
    private final double minTemperature;
    private final double maxTemperature;
    private final String message;

    private ChecklistWeatherResponse(int totalDays, String precipitationType, int precipitationDays,
                                      double minTemperature, double maxTemperature, String message) {
        this.totalDays = totalDays;
        this.precipitationType = precipitationType;
        this.precipitationDays = precipitationDays;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.message = message;
    }

    public static ChecklistWeatherResponse of(LocalDate startDate, LocalDate endDate, List<DailyWeather> forecast) {
        int totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate);
        String precipitationType = precipitationTypeOf(forecast);
        int precipitationDays = precipitationDaysOf(forecast, precipitationType);
        double minTemperature = forecast.stream().mapToDouble(DailyWeather::getMinTemperature).average().orElse(0.0);
        double maxTemperature = forecast.stream().mapToDouble(DailyWeather::getMaxTemperature).average().orElse(0.0);
        return new ChecklistWeatherResponse(
                totalDays, precipitationType, precipitationDays, minTemperature, maxTemperature, messageOf(precipitationType));
    }

    /** 눈 > 비 > 맑음 우선순위로 여행 기간 전체를 대표하는 강수 구분을 정한다. */
    private static String precipitationTypeOf(List<DailyWeather> forecast) {
        if (forecast.stream().anyMatch(day -> day.getWeatherType() == WeatherType.SNOWY)) {
            return SNOWY;
        }
        if (forecast.stream().anyMatch(day -> day.getWeatherType() == WeatherType.RAINY)) {
            return RAINY;
        }
        return SUNNY;
    }

    private static int precipitationDaysOf(List<DailyWeather> forecast, String precipitationType) {
        if (SNOWY.equals(precipitationType)) {
            return (int) forecast.stream().filter(day -> day.getWeatherType() == WeatherType.SNOWY).count();
        }
        if (RAINY.equals(precipitationType)) {
            return (int) forecast.stream().filter(day -> day.getWeatherType() == WeatherType.RAINY).count();
        }
        return 0;
    }

    private static String messageOf(String precipitationType) {
        return switch (precipitationType) {
            case RAINY -> "☔ 비가 예정되어 있어요. 우산과 여벌 양말을 챙기시면 좋아요.";
            case SNOWY -> "❄️ 눈이 예정되어 있어요. 우산과 장갑, 미끄럼 방지 신발을 챙기시면 좋아요.";
            default -> "☀️ 맑은 날씨예요. 선글라스와 자외선 차단제를 챙기시면 좋아요.";
        };
    }
}
