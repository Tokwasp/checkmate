package ssafy.checkmate.domain.checklist.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;

    @PostMapping(value = "/api/v1/scan/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ScanItemResponse>> registerItem(
            @LoginMember MemberPrincipal principal,
            @Valid @ModelAttribute ScanItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success(scanService.registerItem(principal.memberId(), request)));
    }
}
