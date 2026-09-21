package ssafy.checkmate.domain.member.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.checkmate.domain.member.entity.MemberItem;

public interface MemberItemRepository extends JpaRepository<MemberItem, Long> {

    List<MemberItem> findAllByMemberId(Long memberId);

    Optional<MemberItem> findByMemberIdAndName(Long memberId, String name);

    /**
     * 즐겨찾기 물건(10-1번) — id 내림차순 커서 페이징. cursor 가 null 이면 id 가 가장 큰 것부터.
     * 즐겨찾기를 켠 시각은 저장하지 않으므로 "최근"은 물건이 만들어진 순서(id)다.
     */
    @Query("""
            SELECT m FROM MemberItem m
            WHERE m.memberId = :memberId
              AND m.favorite = true
              AND (:cursor IS NULL OR m.id < :cursor)
            ORDER BY m.id DESC
            """)
    List<MemberItem> findFavoriteItems(@Param("memberId") Long memberId, @Param("cursor") Long cursor,
            Pageable pageable);
}
