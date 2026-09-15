package ssafy.checkmate.domain.trip.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.trip.entity.Trip;

public interface TripRepository extends JpaRepository<Trip, Long> {
}
