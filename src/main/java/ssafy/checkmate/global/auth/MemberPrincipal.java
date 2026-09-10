package ssafy.checkmate.global.auth;

import ssafy.checkmate.domain.member.entity.Role;

/**
 * "지금 요청을 보낸 사람"을 나타내는 객체.
 * JWT 토큰에서 꺼낸 값만 담고 있으며 DB 조회는 하지 않는다.
 *
 * 흐름: JwtAuthenticationFilter가 토큰을 검증해 이 객체를 만들어 request attribute에 넣고,
 * LoginMemberArgumentResolver가 그것을 꺼내 컨트롤러의 @LoginMember 파라미터로 넘긴다.
 */
public record MemberPrincipal(Long memberId, Role role) {

    /** 필터가 request.setAttribute 할 때와 리졸버가 getAttribute 할 때 공통으로 쓰는 키 */
    public static final String ATTRIBUTE_KEY = "memberPrincipal";
}
