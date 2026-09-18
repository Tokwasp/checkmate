package ssafy.checkmate.domain.checklist.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.checklist.dto.request.ScanItemRequest;
import ssafy.checkmate.domain.checklist.dto.response.ScanItemResponse;
import ssafy.checkmate.domain.checklist.service.ScanService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;

    @PostMapping(value = "/api/v1/scan/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ScanItemResponse>> registerItem(
            @LoginMember MemberPrincipal principal,
            @Valid @ModelAttribute ScanItemRequest request) {
        log.info("[scan] 등록 요청 memberId={} tripId={} bagId={} itemClassId={} name={} regulationType={} imageBytes={}",
                principal.memberId(), request.getTripId(), request.getBagId(), request.getItemClassId(),
                request.getName(), request.getRegulationType(), imageBytesOf(request));

        ScanItemResponse response = scanService.registerItem(principal.memberId(), request);

        log.info("[scan] 등록 완료 memberId={} tripId={} itemId={} name={}",
                principal.memberId(), request.getTripId(), response.getItemId(), response.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 이미지가 없으면 -1 — 로그 때문에 NPE가 나지 않도록 한다(검증은 @NotNull/서비스가 맡는다). */
    private long imageBytesOf(ScanItemRequest request) {
        return request.getImage() == null ? -1 : request.getImage().getSize();
    }
}
