package ssafy.checkmate.domain.member.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.member.dto.GoogleLoginRequest;
import ssafy.checkmate.domain.member.dto.GoogleLoginResponse;
import ssafy.checkmate.domain.member.service.AuthService;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<GoogleLoginResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {
        GoogleLoginResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
