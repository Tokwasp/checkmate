package ssafy.checkmate.domain.member.dto.response;

import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.entity.Role;

public record MemberResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        Provider provider,
        Role role
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getProvider(),
                member.getRole()
        );
    }
}
