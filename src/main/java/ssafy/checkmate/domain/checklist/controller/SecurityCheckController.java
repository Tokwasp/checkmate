package ssafy.checkmate.domain.checklist.controller;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.checklist.dto.response.SecurityCheckResponse;
import ssafy.checkmate.domain.checklist.service.SecurityCheckService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SecurityCheckController {

    private final SecurityCheckService securityCheckService;

    @PostMapping("/api/v1/trips/{tripId}/security-check")
    public ResponseEntity<ApiResponse<SecurityCheckResponse>> check(
            @LoginMember MemberPrincipal principal,
            @PathVariable Long tripId) {
        log.info("[security-check] 검사 요청 memberId={} tripId={}", principal.memberId(), tripId);
        long startedAt = System.nanoTime();

        SecurityCheckResponse response = securityCheckService.check(tripId, principal.memberId());

        log.info("[security-check] 검사 완료 memberId={} tripId={} 금지={} 주의={} 통과={} 소요={}ms",
                principal.memberId(), tripId, response.getViolationCount(),
                response.getWarningCount(), response.getPassCount(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
