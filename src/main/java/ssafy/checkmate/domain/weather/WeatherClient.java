package ssafy.checkmate.domain.weather;

import java.time.LocalDate;
import java.util.List;

public interface WeatherClient {

    List<DailyWeather> getForecast(String destination, LocalDate startDate, LocalDate endDate);
}
