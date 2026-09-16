package ssafy.checkmate.domain.trip.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.checkmate.domain.trip.entity.Trip;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findAllByMemberId(Long memberId);

    @Query("SELECT t FROM Trip t WHERE t.memberId = :memberId AND t.endDate >= :today ORDER BY t.endDate ASC")
    List<Trip> findUpcomingTrips(@Param("memberId") Long memberId, @Param("today") LocalDate today);

    /** 아직 출발하지 않은(여행 중이 아닌) 여행 중 종료일이 가장 가까운 1건. 출발일이 오늘인 경우도 포함한다. */
    Optional<Trip> findFirstByMemberIdAndStartDateGreaterThanEqualOrderByEndDateAsc(Long memberId, LocalDate startDate);
}
