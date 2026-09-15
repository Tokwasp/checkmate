package ssafy.checkmate.domain.weather;

import java.time.LocalDate;
import lombok.Getter;

@Getter
public class DailyWeather {

    private final LocalDate date;
    private final WeatherType weatherType;
    private final double minTemperature;
    private final double maxTemperature;
    private final int precipitationProbability;

    public DailyWeather(LocalDate date, WeatherType weatherType, double minTemperature,
                         double maxTemperature, int precipitationProbability) {
        this.date = date;
        this.weatherType = weatherType;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.precipitationProbability = precipitationProbability;
    }
}
