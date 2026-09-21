package ssafy.checkmate.domain.member.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * 촬영 물품 반입 판정(35번) 호출 횟수를 <b>조건부로</b> 1 올린다. 30번과 같은 이유로 "확인 후
     * 증가"를 한 문장으로 처리한다.
     *
     * 35번 서비스는 트랜잭션 없이 Opus·Bedrock을 부르므로(긴 외부 호출 동안 DB 연결을 붙잡지
     * 않으려고), 이 UPDATE가 <b>자기 트랜잭션으로 즉시 커밋</b>되도록 여기에 {@code @Transactional}을
     * 둔다. 그래서 뒤 단계가 실패해도 소모한 횟수는 되돌아가지 않는다.
     *
     * @return 1이면 사용 가능(증가됨), 0이면 상한 초과이거나 그런 회원이 없음
     */
    @Transactional
    @Modifying
    @Query("UPDATE Member m SET m.itemAnalysisCount = m.itemAnalysisCount + 1 "
            + "WHERE m.id = :memberId AND m.itemAnalysisCount < :limit")
    int consumeItemAnalysisQuota(@Param("memberId") Long memberId, @Param("limit") int limit);
}