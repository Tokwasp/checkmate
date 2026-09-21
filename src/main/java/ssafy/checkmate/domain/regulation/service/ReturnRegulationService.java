package ssafy.checkmate.domain.regulation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.regulation.dto.response.ReturnRegulationsResponse;
import ssafy.checkmate.domain.regulation.repository.ReturnRegulationRepository;

/**
 * 귀국 규정 안내(34번). 검사가 아니라 고정 안내라서 여행·가방 물품을 조회하지 않고,
 * Bedrock도 부르지 않는다 — 미리 저장해 둔 표를 그대로 읽어 파트별로 나눠 줄 뿐이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReturnRegulationService {

    private final ReturnRegulationRepository returnRegulationRepository;

    public ReturnRegulationsResponse getReturnRegulations() {
        return ReturnRegulationsResponse.of(returnRegulationRepository.findAllByOrderBySortOrderAsc());
    }
}
