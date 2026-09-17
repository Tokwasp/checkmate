package ssafy.checkmate.domain.checklist.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.checkmate.domain.checklist.entity.BagItem;

public interface BagItemRepository extends JpaRepository<BagItem, Long> {

    List<BagItem> findAllByTripId(Long tripId);

    List<BagItem> findAllByBagId(Long bagId);

    /** 물품 인식 등록(26번)의 체크리스트 대조 — 이름이 같고 아직 안 담긴 항목 중 가장 먼저 만들어진 하나 */
    Optional<BagItem> findFirstByTripIdAndNameAndDepartureCheckedFalseOrderByIdAsc(Long tripId, String name);

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
