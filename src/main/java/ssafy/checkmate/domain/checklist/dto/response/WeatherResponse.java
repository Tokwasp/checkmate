package ssafy.checkmate.domain.checklist.dto.response;

import java.time.LocalDate;
import lombok.Getter;
import ssafy.checkmate.domain.weather.DailyWeather;

@Getter
public class WeatherResponse {

    private final LocalDate date;
    private final String weatherType;
    private final double minTemperature;
    private final double maxTemperature;
    private final int precipitationProbability;

    private WeatherResponse(LocalDate date, String weatherType, double minTemperature,
                             double maxTemperature, int precipitationProbability) {
        this.date = date;
        this.weatherType = weatherType;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.precipitationProbability = precipitationProbability;
    }

    public static WeatherResponse of(DailyWeather dailyWeather) {
        return new WeatherResponse(
                dailyWeather.getDate(),
                dailyWeather.getWeatherType().getLabel(),
                dailyWeather.getMinTemperature(),
                dailyWeather.getMaxTemperature(),
                dailyWeather.getPrecipitationProbability());
    }
}
