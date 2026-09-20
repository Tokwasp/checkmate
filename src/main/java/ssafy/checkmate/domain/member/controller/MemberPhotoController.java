package ssafy.checkmate.domain.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ssafy.checkmate.domain.member.dto.response.MemberResponse;
import ssafy.checkmate.domain.member.service.MemberPhotoService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

/** 마이페이지 프로필 사진. */
@RestController
@RequiredArgsConstructor
public class MemberPhotoController {

    private final MemberPhotoService memberPhotoService;

    /**
     * 사진 교체 — multipart 의 {@code image} 파트 하나. 응답은 갱신된 회원 정보(profileImageUrl 포함).
     * {@code required = false} 로 받아 파트가 빠진 경우도 서비스가 400 으로 답하게 한다
     * (필수로 두면 스프링이 먼저 잡아 다른 모양의 에러가 난다).
     */
    @PutMapping(value = "/api/v1/members/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MemberResponse>> updatePhoto(
            @LoginMember MemberPrincipal principal,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(ApiResponse.success(memberPhotoService.updatePhoto(principal.memberId(), image)));
    }

    /** 사진 삭제 — 기본 아이콘으로 돌아간다. 응답은 갱신된 회원 정보. */
    @DeleteMapping("/api/v1/members/me/photo")
    public ResponseEntity<ApiResponse<MemberResponse>> deletePhoto(@LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(memberPhotoService.deletePhoto(principal.memberId())));
    }
}
