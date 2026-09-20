package ssafy.checkmate.domain.member.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.member.entity.MemberItem;

public interface MemberItemRepository extends JpaRepository<MemberItem, Long> {

    List<MemberItem> findAllByMemberId(Long memberId);

    Optional<MemberItem> findByMemberIdAndName(Long memberId, String name);
}
