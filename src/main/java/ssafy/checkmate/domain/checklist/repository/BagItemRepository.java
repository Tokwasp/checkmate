package ssafy.checkmate.domain.checklist.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.checklist.entity.BagItem;

public interface BagItemRepository extends JpaRepository<BagItem, Long> {

    List<BagItem> findAllByTripId(Long tripId);

    List<BagItem> findAllByBagId(Long bagId);

    long countByTripId(Long tripId);

    long countByTripIdAndDepartureChecked(Long tripId, boolean departureChecked);

    void deleteAllByTripId(Long tripId);
}
