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

    /**
     * 귀가 전 점검(31번) — 그 여행에서 실제로 가방에 담은 물품 전부.
     * 체크 여부로 정렬하지 않는다. 타일을 누를 때마다 그리드가 재배치되면 옆 칸을 잘못 누르게 된다.
     */
    List<BagItem> findAllByTripIdAndDepartureCheckedOrderByIdAsc(Long tripId, boolean departureChecked);

    /** 물품 인식 등록(26번)의 체크리스트 대조 — 그 가방 안에서 이름이 같고 아직 안 담긴 항목 중 가장 먼저 만들어진 하나 */
    Optional<BagItem> findFirstByTripIdAndBagIdAndNameAndDepartureCheckedFalseOrderByIdAsc(
            Long tripId, Long bagId, String name);

    /** 물품 인식 등록(26번)의 수량 합산 대조 — 그 가방 안에서 이름이 같고 이미 담긴 항목 중 가장 먼저 만들어진 하나 */
    Optional<BagItem> findFirstByTripIdAndBagIdAndNameAndDepartureCheckedTrueOrderByIdAsc(
            Long tripId, Long bagId, String name);

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
