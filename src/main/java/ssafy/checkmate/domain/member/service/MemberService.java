package ssafy.checkmate.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.member.dto.MemberResponse;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    /**
     * 토큰에서 꺼낸 memberId로 회원을 조회한다.
     * 토큰은 유효하지만 회원이 DB에 없는 경우(탈퇴 후 토큰 재사용 등)를 여기서 걸러낸다.
     */
    public MemberResponse getMe(Long memberId) {
        Member member = memberRepository.findById(memberId)
                // TODO(예외 인프라): GlobalExceptionHandler/ErrorCode 도입 후 MemberNotFoundException(404)으로 교체
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + memberId));
        return MemberResponse.from(member);
    }
}
