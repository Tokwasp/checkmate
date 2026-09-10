package ssafy.checkmate.domain.member.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import ssafy.checkmate.domain.member.service.oauth.GoogleUserInfo;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GoogleLoginResponse {

    private final String email;
    private final String nickname;
    private final String profileImageUrl;

    public static GoogleLoginResponse of(GoogleUserInfo info) {
        return new GoogleLoginResponse(info.getEmail(), info.getNickname(), info.getProfileImageUrl());
    }
}
