package ssafy.checkmate.domain.checklist.controller;

import jakarta.validation.Valid;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.checklist.dto.request.QuarantineCheckRequest;
import ssafy.checkmate.domain.checklist.dto.response.QuarantineCheckResponse;
import ssafy.checkmate.domain.checklist.service.QuarantineCheckService;
import ssafy.checkmate.global.auth.LoginMember;
import ssafy.checkmate.global.auth.MemberPrincipal;
import ssafy.checkmate.global.response.ApiResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
public class QuarantineCheckController {

    private final QuarantineCheckService quarantineCheckService;

    @PostMapping(value = "/api/v1/scan/quarantine-check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<QuarantineCheckResponse>> check(
            @LoginMember MemberPrincipal principal,
            @Valid @ModelAttribute QuarantineCheckRequest request) {
        log.info("[quarantine-check] 판정 요청 memberId={} imageBytes={}",
                principal.memberId(), imageBytesOf(request));
        long startedAt = System.nanoTime();

        QuarantineCheckResponse response = quarantineCheckService.check(principal.memberId(), request);

        log.info("[quarantine-check] 판정 완료 memberId={} 품목={} 판정={} 소요={}ms",
                principal.memberId(), response.getItem().getName(), response.getVerdict().getLevel().getLabel(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 이미지가 없으면 -1 — 로그 때문에 NPE가 나지 않도록 한다(검증은 @NotNull/ScanImage가 맡는다). */
    private long imageBytesOf(QuarantineCheckRequest request) {
        return request.getImage() == null ? -1 : request.getImage().getSize();
    }
}
