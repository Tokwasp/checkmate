package ssafy.checkmate.domain.weather.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.weather.entity.TripWeather;

public interface TripWeatherRepository extends JpaRepository<TripWeather, Long> {

    List<TripWeather> findAllByTripIdOrderByDateAsc(Long tripId);

    void deleteAllByTripId(Long tripId);
}
