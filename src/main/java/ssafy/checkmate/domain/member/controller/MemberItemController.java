package ssafy.checkmate.domain.member.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.member.dto.request.MemberFavoritesRequest;
import ssafy.checkmate.domain.member.dto.request.MemberItemFavoriteRequest;
import ssafy.checkmate.domain.member.dto.response.MemberItemResponse;
import ssafy.checkmate.domain.member.dto.response.MemberItemsResponse;
import ssafy.checkmate.domain.member.service.MemberItemService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

/** 마이페이지 「내 물건」. */
@RestController
@RequiredArgsConstructor
public class MemberItemController {

    private final MemberItemService memberItemService;

    /** 내 물건 목록 — 담아 온 물건(횟수 포함)과 즐겨찾기. */
    @GetMapping("/api/v1/members/me/items")
    public ResponseEntity<ApiResponse<MemberItemsResponse>> getMyItems(@LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(memberItemService.getMyItems(principal.memberId())));
    }

    /** 즐겨찾기 켜기/끄기 — 본문 { "isFavorite": true }. */
    @PatchMapping("/api/v1/members/me/items/{itemId}/favorite")
    public ResponseEntity<ApiResponse<MemberItemResponse>> setFavorite(
            @PathVariable Long itemId,
            @LoginMember MemberPrincipal principal,
            @Valid @RequestBody MemberItemFavoriteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                memberItemService.setFavorite(principal.memberId(), itemId, request.getIsFavorite())));
    }

    /** 여러 물건을 한 번에 즐겨찾기로 (이름 기준) — 인벤토리의 「즐겨찾기」 가 부른다. 응답은 갱신된 목록 전체. */
    @PostMapping("/api/v1/members/me/items/favorites")
    public ResponseEntity<ApiResponse<MemberItemsResponse>> addFavorites(
            @LoginMember MemberPrincipal principal,
            @Valid @RequestBody MemberFavoritesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                memberItemService.addFavorites(principal.memberId(), request.getItems())));
    }
}
