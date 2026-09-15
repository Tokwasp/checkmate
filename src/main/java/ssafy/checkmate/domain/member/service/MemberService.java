package ssafy.checkmate.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.member.dto.response.MemberResponse;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberResponse getMe(Long memberId) {
        Member member = memberRepository.findById(memberId)
                // TODO(예외 인프라): GlobalExceptionHandler/ErrorCode 도입 후 MemberNotFoundException(404)으로 교체
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + memberId));
        return MemberResponse.from(member);
    }
}
