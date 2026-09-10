package ssafy.checkmate.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.auth.dto.TokenResponse;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.global.auth.JwtTokenProvider;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;

    /**
     * 인증이 끝난 회원에게 토큰을 발급한다.
     *
     * 소셜 로그인 담당자가 호출할 접점(seam):
     *   카카오/구글 검증 → MemberRepository.findByEmail 또는 신규 저장 → authService.login(member)
     * 이 메서드는 "이 회원이 누구인지 이미 확인됐다"고 믿고 토큰만 만든다. 검증 책임은 호출하는 쪽에 있다.
     */
    public TokenResponse login(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        return new TokenResponse(accessToken);
    }

    /**
     * 개발용 로그인. 이메일로 회원을 찾고, 없으면 만들어서 토큰을 발급한다.
     * 소셜 로그인 없이 인증이 필요한 API를 테스트하기 위한 것이며,
     * DevAuthController가 auth.dev-login.enabled=true 일 때만 존재하므로 운영에서는 호출될 수 없다.
     */
    public TokenResponse devLogin(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .email(email)
                        .nickname(email.substring(0, email.indexOf('@')))
                        .build()));
        return login(member);
    }
}
