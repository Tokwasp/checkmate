package ssafy.checkmate.domain.member.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.member.dto.request.DevLoginRequest;
import ssafy.checkmate.domain.member.dto.response.TokenResponse;
import ssafy.checkmate.domain.member.service.AuthService;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.dev-login", name = "enabled", havingValue = "true")
public class DevAuthController {

    private final AuthService authService;

    @PostMapping("/dev-login")
    public ResponseEntity<ApiResponse<TokenResponse>> devLogin(@Valid @RequestBody DevLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.devLogin(request.getEmail())));
    }
}
