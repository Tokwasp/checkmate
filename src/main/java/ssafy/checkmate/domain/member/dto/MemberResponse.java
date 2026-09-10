package ssafy.checkmate.domain.member.dto;

import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.entity.Role;

/**
 * 회원 정보 응답 DTO. 엔티티를 직접 반환하지 않고 필요한 필드만 골라 내려준다.
 * (엔티티를 그대로 내려주면 연관관계/내부 필드가 노출되고, 엔티티 변경이 API 스펙 변경으로 번진다)
 */
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
