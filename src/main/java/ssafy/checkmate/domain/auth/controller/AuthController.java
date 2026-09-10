package ssafy.checkmate.domain.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.member.dto.MemberResponse;
import ssafy.checkmate.domain.member.service.MemberService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    /**
     * 내 정보 조회. 앱이 시작할 때 저장된 토큰이 아직 유효한지 확인하는 용도로도 쓰인다.
     * 이 요청은 JwtAuthenticationFilter를 통과해야만 여기 도달하므로 principal은 항상 채워져 있다.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(@LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(memberService.getMe(principal.memberId())));
    }
}
