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

    /**
     * 물품 인식 등록(26번)의 체크리스트 대조 2차 — 가방을 가리지 않고 그 여행에서 이름이 같고 아직 안 담긴 항목.
     * 체크리스트를 처음 만들면 모든 항목이 기본 가방(NONE)에 있으므로, 인식 화면에서 고른 가방과 다를 수밖에 없다.
     * 그 항목을 새로 만들지 않고 고른 가방으로 옮겨 담기 위해 쓴다 (2026-09-20).
     */
    Optional<BagItem> findFirstByTripIdAndNameAndDepartureCheckedFalseOrderByIdAsc(Long tripId, String name);

    /** 물품 인식 등록(26번)의 수량 합산 대조 — 그 가방 안에서 이름이 같고 이미 담긴 항목 중 가장 먼저 만들어진 하나 */
    Optional<BagItem> findFirstByTripIdAndBagIdAndNameAndDepartureCheckedTrueOrderByIdAsc(
            Long tripId, Long bagId, String name);

    long countByTripId(Long tripId);

    long countByTripIdAndDepartureChecked(Long tripId, boolean departureChecked);

    long countByBagIdAndDepartureChecked(Long bagId, boolean departureChecked);

    /** 회원이 특정 이름의 물건을 담은 횟수 — 즐겨찾기 토글 응답이 한 건만 다시 셀 때 쓴다 */
    long countByMemberIdAndNameAndDepartureChecked(Long memberId, String name, boolean departureChecked);

    /**
     * 회원이 지금까지 담은(departureChecked) 물품을 이름별로 센다 — 마이페이지 「내 물건」의 담은 횟수.
     * 여행을 가로질러 세므로 tripId 조건이 없다. 행 수를 세므로 수량(quantity)은 보지 않는다:
     * "여권을 세 여행에 담았다" 는 3 이고, "양말을 한 번에 다섯 켤레 담았다" 는 1 이다.
     * 분류는 이름마다 하나만 대표로 든다(MAX) — 같은 이름이 여행마다 다르게 분류될 수 있기 때문.
     */
    @Query("""
            SELECT b.name AS name, MAX(b.category) AS category, COUNT(b) AS packedCount
            FROM BagItem b
            WHERE b.memberId = :memberId
              AND b.departureChecked = true
            GROUP BY b.name
            """)
    List<PackedNameCount> countPackedByMember(@Param("memberId") Long memberId);

    void deleteAllByTripId(Long tripId);

    @Query("SELECT DISTINCT b.category FROM BagItem b WHERE b.tripId = :tripId AND b.departureChecked = true")
    List<String> findDistinctCategoriesByTripIdAndDepartureChecked(@Param("tripId") Long tripId);

    @Query("SELECT DISTINCT b.category FROM BagItem b WHERE b.bagId = :bagId AND b.departureChecked = true")
    List<String> findDistinctCategoriesByBagIdAndDepartureChecked(@Param("bagId") Long bagId);

    /**
     * 귀가 전 점검 확인 목록(32번) — 방금 카메라가 찾아준 것이 맨 위에 보여야 한다.
     * updatedAt은 체크 전용 시각이 아니라 행이 바뀐 마지막 시각이라 같은 값이 나올 수 있어
     * id 내림차순을 2차 기준으로 둔다.
     */
    @Query("""
            SELECT b FROM BagItem b
            WHERE b.tripId = :tripId
              AND b.departureChecked = true
              AND b.returnChecked = true
            ORDER BY b.updatedAt DESC, b.id DESC
            """)
    List<BagItem> findReturnCheckedItems(@Param("tripId") Long tripId);

    /** 귀가 전 점검 안 보임 목록(32번) — 누르는 동안 순서가 흔들리면 옆 줄을 잘못 누른다. */
    @Query("""
            SELECT b FROM BagItem b
            WHERE b.tripId = :tripId
              AND b.departureChecked = true
              AND b.returnChecked = false
            ORDER BY b.id ASC
            """)
    List<BagItem> findReturnUncheckedItems(@Param("tripId") Long tripId);

    /**
     * 인식한 물품 체크(33번) — 이름이 정확히 같은 미확인 물품. 가방은 가리지 않는다.
     * 호출부가 가장 먼저 만들어진 하나만 쓴다(하나만 챙겼는데 여러 개가 체크되면 안 된다).
     */
    @Query("""
            SELECT b FROM BagItem b
            WHERE b.tripId = :tripId
              AND b.name = :name
              AND b.departureChecked = true
              AND b.returnChecked = false
            ORDER BY b.id ASC
            """)
    List<BagItem> findReturnUncheckedItemsByName(@Param("tripId") Long tripId, @Param("name") String name);

    @Query("""
            SELECT COUNT(b) FROM BagItem b
            WHERE b.tripId = :tripId
              AND b.departureChecked = true
              AND b.returnChecked = false
            """)
    long countReturnUncheckedItems(@Param("tripId") Long tripId);

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
