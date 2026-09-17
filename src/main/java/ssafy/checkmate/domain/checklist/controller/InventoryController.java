package ssafy.checkmate.domain.checklist.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.checklist.dto.request.BagItemUpdateRequest;
import ssafy.checkmate.domain.checklist.service.InventoryService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PatchMapping("/api/v1/inventories/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> updateItem(
            @PathVariable Long itemId,
            @LoginMember MemberPrincipal principal,
            @Valid @RequestBody BagItemUpdateRequest request) {
        inventoryService.updateItem(itemId, principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/api/v1/inventories/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @PathVariable Long itemId,
            @LoginMember MemberPrincipal principal) {
        inventoryService.deleteItem(itemId, principal.memberId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
