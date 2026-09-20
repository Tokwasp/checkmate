package ssafy.checkmate.domain.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
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
}
