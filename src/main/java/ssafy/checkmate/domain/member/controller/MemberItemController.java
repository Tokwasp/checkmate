package ssafy.checkmate.domain.member.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
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

    /**
     * 물건 대표 사진 교체 — multipart 파트 image 하나. 「내 물건」 카메라 등록이 누끼를 올릴 때 쓴다.
     * required=false 로 받아 파트가 빠져도 서비스가 400 으로 답한다.
     */
    @PutMapping(value = "/api/v1/members/me/items/{itemId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MemberItemResponse>> updateImage(
            @PathVariable Long itemId,
            @LoginMember MemberPrincipal principal,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(ApiResponse.success(memberItemService.updateImage(principal.memberId(), itemId, image)));
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
