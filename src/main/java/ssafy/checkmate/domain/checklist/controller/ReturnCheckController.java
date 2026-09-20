package ssafy.checkmate.domain.checklist.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckResponse;
import ssafy.checkmate.domain.checklist.service.ReturnCheckService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@RequiredArgsConstructor
public class ReturnCheckController {

    private final ReturnCheckService returnCheckService;

    @GetMapping("/api/v1/trips/{tripId}/return-check")
    public ResponseEntity<ApiResponse<ReturnCheckResponse>> getReturnCheck(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                returnCheckService.getReturnCheck(tripId, principal.memberId())));
    }
}
