package ssafy.checkmate.domain.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.auth.dto.DevLoginRequest;
import ssafy.checkmate.domain.auth.dto.TokenResponse;
import ssafy.checkmate.domain.auth.service.AuthService;
import ssafy.checkmate.global.response.ApiResponse;

/**
 * 개발 환경 전용 로그인 컨트롤러.
 *
 * @ConditionalOnProperty: yaml의 auth.dev-login.enabled 가 true 일 때만 이 클래스가 빈으로 등록된다.
 * false 이거나 값이 없으면 빈 자체가 만들어지지 않아 엔드포인트가 존재하지 않는다(404).
 * 운영 .env 에서는 DEV_LOGIN_ENABLED 를 비워두거나 false 로 둔다.
 *
 * 경로 /api/v1/auth/dev-login 은 AuthConfig 의 WHITELIST 에 있어 JwtAuthenticationFilter 를 거치지 않는다.
 */
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
