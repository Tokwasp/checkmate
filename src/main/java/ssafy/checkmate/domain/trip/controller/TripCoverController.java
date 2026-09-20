package ssafy.checkmate.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ssafy.checkmate.domain.trip.dto.response.TripCoverResponse;
import ssafy.checkmate.domain.trip.service.TripCoverService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

/** 여행 표지 사진 — 마이페이지 여행 기록 폴라로이드. */
@RestController
@RequiredArgsConstructor
public class TripCoverController {

    private final TripCoverService tripCoverService;

    /**
     * 표지 교체 — multipart 의 {@code image} 파트 하나. {@code required = false} 로 받아 파트가 빠진 경우도
     * 서비스가 400 으로 답하게 한다(필수로 두면 스프링이 먼저 잡아 다른 모양의 에러가 난다).
     */
    @PutMapping(value = "/api/v1/trips/{tripId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TripCoverResponse>> updateCover(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(ApiResponse.success(tripCoverService.updateCover(tripId, principal.memberId(), image)));
    }

    /** 표지 삭제 — 기본 그림으로 돌아간다. */
    @DeleteMapping("/api/v1/trips/{tripId}/cover")
    public ResponseEntity<ApiResponse<TripCoverResponse>> deleteCover(
            @PathVariable Long tripId,
            @LoginMember MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(tripCoverService.deleteCover(tripId, principal.memberId())));
    }
}
