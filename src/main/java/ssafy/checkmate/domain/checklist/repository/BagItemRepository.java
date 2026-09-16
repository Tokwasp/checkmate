package ssafy.checkmate.domain.checklist.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.checkmate.domain.checklist.entity.BagItem;

public interface BagItemRepository extends JpaRepository<BagItem, Long> {

    List<BagItem> findAllByTripId(Long tripId);

    List<BagItem> findAllByBagId(Long bagId);

    long countByTripId(Long tripId);

    long countByTripIdAndDepartureChecked(Long tripId, boolean departureChecked);

    long countByBagIdAndDepartureChecked(Long bagId, boolean departureChecked);

    void deleteAllByTripId(Long tripId);

    @Query("SELECT DISTINCT b.category FROM BagItem b WHERE b.tripId = :tripId AND b.departureChecked = true")
    List<String> findDistinctCategoriesByTripIdAndDepartureChecked(@Param("tripId") Long tripId);

    @Query("SELECT DISTINCT b.category FROM BagItem b WHERE b.bagId = :bagId AND b.departureChecked = true")
    List<String> findDistinctCategoriesByBagIdAndDepartureChecked(@Param("bagId") Long bagId);

    @Query("""
            SELECT b FROM BagItem b
            WHERE b.tripId = :tripId
              AND b.departureChecked = true
              AND (:bagId IS NULL OR b.bagId = :bagId)
              AND (:category IS NULL OR b.category = :category)
              AND (:lastItemId IS NULL OR b.id < :lastItemId)
            ORDER BY b.id DESC
            """)
    List<BagItem> findInventoryItems(@Param("tripId") Long tripId, @Param("bagId") Long bagId,
            @Param("category") String category, @Param("lastItemId") Long lastItemId, Pageable pageable);
}
