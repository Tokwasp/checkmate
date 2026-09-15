package ssafy.checkmate.domain.trip.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.trip.entity.Trip;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findAllByMemberId(Long memberId);
}
