package ssafy.checkmate.domain.member.dto.response;

import ssafy.checkmate.domain.member.entity.Member;

public record GoogleLoginResponse(String accessToken, boolean isNewUser, MemberResponse member) {

    public static GoogleLoginResponse of(String accessToken, boolean isNewUser, Member member) {
        return new GoogleLoginResponse(accessToken, isNewUser, MemberResponse.from(member));
    }
}
