package ssafy.checkmate.domain.checklist.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.checklist.dto.request.BagCreateRequest;
import ssafy.checkmate.domain.checklist.dto.request.BagUpdateRequest;
import ssafy.checkmate.domain.checklist.dto.response.BagResponse;
import ssafy.checkmate.domain.checklist.service.BagService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@RequiredArgsConstructor
public class BagController {

    private final BagService bagService;

    @PostMapping("/api/v1/trips/{tripId}/bags")
    public ResponseEntity<ApiResponse<BagResponse>> createBag(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal,
            @Valid @RequestBody BagCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bagService.createBag(tripId, principal.memberId(), request)));
    }

    @PatchMapping("/api/v1/bags/{bagId}")
    public ResponseEntity<ApiResponse<BagResponse>> updateBag(
            @PathVariable Long bagId,
            @LoginMember MemberPrincipal principal,
            @RequestBody BagUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bagService.updateBag(bagId, principal.memberId(), request)));
    }

    @DeleteMapping("/api/v1/bags/{bagId}")
    public ResponseEntity<ApiResponse<Void>> deleteBag(
            @PathVariable Long bagId,
            @LoginMember MemberPrincipal principal) {
        bagService.deleteBag(bagId, principal.memberId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
