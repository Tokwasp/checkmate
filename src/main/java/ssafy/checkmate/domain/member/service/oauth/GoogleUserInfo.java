package ssafy.checkmate.domain.member.service.oauth;

import lombok.Getter;

@Getter
public class GoogleUserInfo {

    private final String email;
    private final String nickname;
    private final String profileImageUrl;

    public GoogleUserInfo(String email, String nickname, String profileImageUrl) {
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }
}
