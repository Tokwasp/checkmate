package ssafy.checkmate.domain.weather.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.domain.weather.DailyWeather;
import ssafy.checkmate.domain.weather.WeatherType;
import ssafy.checkmate.global.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripWeather extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tripId;

    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private WeatherType weatherType;

    private double minTemperature;

    private double maxTemperature;

    private int precipitationProbability;

    @Builder(access = AccessLevel.PRIVATE)
    private TripWeather(Long tripId, LocalDate date, WeatherType weatherType,
                         double minTemperature, double maxTemperature, int precipitationProbability) {
        this.tripId = tripId;
        this.date = date;
        this.weatherType = weatherType;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.precipitationProbability = precipitationProbability;
    }

    public static TripWeather of(Long tripId, DailyWeather dailyWeather) {
        return TripWeather.builder()
                .tripId(tripId)
                .date(dailyWeather.getDate())
                .weatherType(dailyWeather.getWeatherType())
                .minTemperature(dailyWeather.getMinTemperature())
                .maxTemperature(dailyWeather.getMaxTemperature())
                .precipitationProbability(dailyWeather.getPrecipitationProbability())
                .build();
    }

    public DailyWeather toDailyWeather() {
        return new DailyWeather(date, weatherType, minTemperature, maxTemperature, precipitationProbability);
    }
}
