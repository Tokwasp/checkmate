package ssafy.checkmate.domain.member.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ssafy.checkmate.domain.member.dto.request.GoogleLoginRequest;
import ssafy.checkmate.domain.member.dto.response.GoogleLoginResponse;
import ssafy.checkmate.domain.member.dto.response.MemberResponse;
import ssafy.checkmate.domain.member.service.AuthService;
import ssafy.checkmate.domain.member.service.MemberService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MemberService memberService;
    private final AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<GoogleLoginResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {
        GoogleLoginResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(@LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(memberService.getMe(principal.memberId())));
    }
}
