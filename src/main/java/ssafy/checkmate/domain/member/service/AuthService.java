package ssafy.checkmate.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.member.dto.request.GoogleLoginRequest;
import ssafy.checkmate.domain.member.dto.response.GoogleLoginResponse;
import ssafy.checkmate.domain.member.dto.response.TokenResponse;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.domain.member.service.oauth.GoogleIdTokenParser;
import ssafy.checkmate.domain.member.service.oauth.GoogleUserInfo;
import ssafy.checkmate.global.auth.JwtTokenProvider;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final GoogleIdTokenParser googleIdTokenParser;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;

    public GoogleLoginResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleUserInfo userInfo = googleIdTokenParser.parse(request.getIdToken());

        Optional<Member> findMember = memberRepository.findByEmail(userInfo.getEmail());
        Member member = findMember.orElseGet(() -> memberRepository.save(Member.builder()
                .email(userInfo.getEmail())
                .nickname(userInfo.getNickname())
                .profileImageUrl(userInfo.getProfileImageUrl())
                .provider(Provider.GOOGLE)
                .build()));

        TokenResponse token = createAccessToken(member);
        return GoogleLoginResponse.of(token.accessToken(), findMember.isEmpty(), member);
    }

    public TokenResponse createAccessToken(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        return new TokenResponse(accessToken);
    }

    public TokenResponse devLogin(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .email(email)
                        .nickname(email.substring(0, email.indexOf('@')))
                        .build()));
        return createAccessToken(member);
    }
}
