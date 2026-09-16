package ssafy.checkmate.domain.checklist.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.checklist.dto.request.ChecklistItemUpdateRequest;
import ssafy.checkmate.domain.checklist.dto.request.ChecklistItemsCreateRequest;
import ssafy.checkmate.domain.checklist.dto.request.ChecklistItemsMoveRequest;
import ssafy.checkmate.domain.checklist.dto.response.ChecklistItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.ChecklistItemsResponse;
import ssafy.checkmate.domain.checklist.dto.response.ChecklistResponse;
import ssafy.checkmate.domain.checklist.dto.response.InventoryBagsResponse;
import ssafy.checkmate.domain.checklist.dto.response.InventoryItemsResponse;
import ssafy.checkmate.domain.checklist.service.ChecklistService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;

    @PostMapping("/api/v1/trips/{tripId}/checklists/recommend")
    public ResponseEntity<ApiResponse<ChecklistItemsResponse>> recommend(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(checklistService.recommend(tripId, principal.memberId())));
    }

    @GetMapping("/api/v1/trips/{tripId}/checklists")
    public ResponseEntity<ApiResponse<ChecklistResponse>> getChecklist(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(checklistService.getChecklist(tripId, principal.memberId())));
    }

    @PostMapping("/api/v1/trips/{tripId}/checklists/items")
    public ResponseEntity<ApiResponse<ChecklistItemsResponse>> addItems(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal,
            @Valid @RequestBody ChecklistItemsCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(checklistService.addItems(tripId, principal.memberId(), request)));
    }

    @PatchMapping("/api/v1/checklists/items/{itemId}")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> updateItem(
            @PathVariable Long itemId,
            @LoginMember MemberPrincipal principal,
            @RequestBody ChecklistItemUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(checklistService.updateItem(itemId, principal.memberId(), request)));
    }

    @PatchMapping("/api/v1/trips/{tripId}/checklists/items/bag")
    public ResponseEntity<ApiResponse<ChecklistItemsResponse>> moveItemsToBag(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal,
            @Valid @RequestBody ChecklistItemsMoveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(checklistService.moveItemsToBag(tripId, principal.memberId(), request)));
    }

    @DeleteMapping("/api/v1/checklists/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @PathVariable Long itemId,
            @LoginMember MemberPrincipal principal) {
        checklistService.deleteItem(itemId, principal.memberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/api/v1/trips/{tripId}/checklists/inventory/bags")
    public ResponseEntity<ApiResponse<InventoryBagsResponse>> getInventoryBags(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(checklistService.getInventoryBags(tripId, principal.memberId())));
    }

    @GetMapping("/api/v1/trips/{tripId}/checklists/inventory/items")
    public ResponseEntity<ApiResponse<InventoryItemsResponse>> getInventoryItems(
            @PathVariable Long tripId,
            @RequestParam(required = false) Long bagId,
            @RequestParam String category,
            @RequestParam(required = false) Long lastItemId,
            @LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                checklistService.getInventoryItems(tripId, principal.memberId(), bagId, category, lastItemId)));
    }
}
