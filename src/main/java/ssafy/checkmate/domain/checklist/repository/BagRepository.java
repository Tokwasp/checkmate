package ssafy.checkmate.domain.checklist.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagType;

public interface BagRepository extends JpaRepository<Bag, Long> {

    Optional<Bag> findByTripIdAndType(Long tripId, BagType type);
}
