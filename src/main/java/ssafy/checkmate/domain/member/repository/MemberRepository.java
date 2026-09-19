package ssafy.checkmate.domain.member.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.checkmate.domain.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);

    /**
     * 반입 규정 검사(30번) 호출 횟수를 <b>조건부로</b> 1 올린다. 상한 미만일 때만 올라가므로
     * "확인 후 증가"를 한 문장으로 처리한다 — 따로 조회한 뒤 증가시키면 동시 요청 두 개가
     * 같은 값을 읽고 둘 다 통과한다.
     *
     * @return 1이면 사용 가능(증가됨), 0이면 상한 초과이거나 그런 회원이 없음
     */
    @Modifying
    @Query("UPDATE Member m SET m.securityCheckCount = m.securityCheckCount + 1 "
            + "WHERE m.id = :memberId AND m.securityCheckCount < :limit")
    int consumeSecurityCheckQuota(@Param("memberId") Long memberId, @Param("limit") int limit);
}