package ssafy.checkmate.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ssafy.checkmate.domain.member.dto.GoogleLoginRequest;
import ssafy.checkmate.domain.member.dto.GoogleLoginResponse;
import ssafy.checkmate.domain.member.service.oauth.GoogleIdTokenParser;
import ssafy.checkmate.domain.member.service.oauth.GoogleUserInfo;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleIdTokenParser googleIdTokenParser;

    public GoogleLoginResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleUserInfo userInfo = googleIdTokenParser.parse(request.getIdToken());
        return GoogleLoginResponse.of(userInfo);
    }
}
