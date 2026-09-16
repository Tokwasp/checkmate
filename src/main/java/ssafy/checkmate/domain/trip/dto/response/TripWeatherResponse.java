package ssafy.checkmate.domain.trip.dto.response;

import java.time.LocalDate;
import lombok.Getter;
import ssafy.checkmate.domain.weather.DailyWeather;

@Getter
public class TripWeatherResponse {

    private final LocalDate date;
    private final String weatherType;
    private final double minTemperature;
    private final double maxTemperature;
    private final int precipitationProbability;

    private TripWeatherResponse(LocalDate date, String weatherType, double minTemperature,
                                 double maxTemperature, int precipitationProbability) {
        this.date = date;
        this.weatherType = weatherType;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.precipitationProbability = precipitationProbability;
    }

    public static TripWeatherResponse of(DailyWeather dailyWeather) {
        return new TripWeatherResponse(
                dailyWeather.getDate(),
                dailyWeather.getWeatherType().getLabel(),
                dailyWeather.getMinTemperature(),
                dailyWeather.getMaxTemperature(),
                dailyWeather.getPrecipitationProbability());
    }
}
