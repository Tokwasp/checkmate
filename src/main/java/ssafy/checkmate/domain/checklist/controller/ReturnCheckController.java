package ssafy.checkmate.domain.checklist.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.checklist.dto.request.ReturnCheckItemCheckRequest;
import ssafy.checkmate.domain.checklist.dto.request.ReturnCheckScanRequest;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckItemCheckResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckItemsResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckScanResponse;
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

    @GetMapping("/api/v1/trips/{tripId}/return-check/items")
    public ResponseEntity<ApiResponse<ReturnCheckItemsResponse>> getItems(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                returnCheckService.getItems(tripId, principal.memberId())));
    }

    @PostMapping("/api/v1/trips/{tripId}/return-check/scan")
    public ResponseEntity<ApiResponse<ReturnCheckScanResponse>> scan(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal,
            @Valid @RequestBody ReturnCheckScanRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnCheckService.scan(tripId, principal.memberId(), request)));
    }

    /** 33-2번. 손으로 한 줄 체크·해제. 카메라가 켠 줄을 되돌리는 것도 이 API다. */
    @PatchMapping("/api/v1/trips/{tripId}/return-check/items/{itemId}")
    public ResponseEntity<ApiResponse<ReturnCheckItemCheckResponse>> checkItem(
            @PathVariable Long tripId,
            @PathVariable Long itemId,
            @LoginMember MemberPrincipal principal,
            @Valid @RequestBody ReturnCheckItemCheckRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                returnCheckService.checkItem(tripId, principal.memberId(), itemId, request)));
    }
}
