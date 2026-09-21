package ssafy.checkmate.domain.regulation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.checkmate.domain.regulation.dto.response.ReturnRegulationsResponse;
import ssafy.checkmate.domain.regulation.service.ReturnRegulationService;
import ssafy.checkmate.global.response.ApiResponse;

@RestController
@RequiredArgsConstructor
public class ReturnRegulationController {

    private final ReturnRegulationService returnRegulationService;

    @GetMapping("/api/v1/return-regulations")
    public ResponseEntity<ApiResponse<ReturnRegulationsResponse>> getReturnRegulations() {
        return ResponseEntity.ok(ApiResponse.success(returnRegulationService.getReturnRegulations()));
    }
}
