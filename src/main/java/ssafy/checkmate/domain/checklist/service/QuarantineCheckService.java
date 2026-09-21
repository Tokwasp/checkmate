package ssafy.checkmate.domain.checklist.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.checkmate.domain.checklist.dto.request.QuarantineCheckRequest;
import ssafy.checkmate.domain.checklist.dto.response.QuarantineCheckResponse;
import ssafy.checkmate.domain.checklist.exception.ItemAnalysisFailedException;
import ssafy.checkmate.domain.checklist.exception.ItemAnalysisLimitExceededException;
import ssafy.checkmate.domain.checklist.exception.ItemNotRecognizedException;
import ssafy.checkmate.domain.checklist.exception.QuarantineCheckFailedException;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.global.exception.BusinessException;

/**
 * 촬영 물품 반입 판정(35번). 사진 한 장을 ① VLM으로 읽고 ② 한국 세관·검역 규정을 검색해
 * ③ 판정을 만든다. 분석만 하고 인벤토리에는 저장하지 않는다 — DB에 쓰는 것은 호출 횟수뿐이다.
 *
 * <b>이 서비스에는 트랜잭션을 걸지 않는다.</b> Opus 비전 호출과 Bedrock 검색·생성이 직렬로 수 초 이상
 * 걸리는데, 그동안 DB 연결을 붙잡고 있을 이유가 없다. 횟수 소모는
 * {@link MemberRepository#consumeItemAnalysisQuota}가 자기 트랜잭션으로 즉시 커밋하므로, 뒤 단계가
 * 실패(422·500)해도 <b>소모한 횟수는 되돌아가지 않는다</b> — 비용은 이미 나갔고 되돌리면 같은 요청을
 * 무한히 재시도할 수 있어 상한이 무력해지기 때문이다(30번과 같은 이유).
 *
 * 로그는 EC2에서 흐름을 따라갈 수 있도록 {@code [quarantine-check]} 접두사로 남긴다. 사진 내용
 * (base64)은 남기지 않고 크기만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuarantineCheckService {

    /**
     * 계정당 허용하는 누적 판정 횟수(30번과 별도로 센다). Opus 비전과 Bedrock 호출이 건당 나가므로
     * 상한이 없으면 비용이 무한정 늘어난다.
     */
    private static final int ITEM_ANALYSIS_LIMIT = 20;

    private final MemberRepository memberRepository;
    private final ItemAnalysisClient itemAnalysisClient;
    private final QuarantineRegulationClient quarantineRegulationClient;

    public QuarantineCheckResponse check(Long memberId, QuarantineCheckRequest request) {
        ScanImage image = ScanImage.from(request.getImage());
        log.info("[quarantine-check] 이미지 검증 통과 memberId={} mimeType={} bytes={}",
                memberId, image.mimeType(), image.bytes().length);

        consumeQuota(memberId);
        ItemAnalysis item = analyze(memberId, image);
        QuarantineVerdict verdict = judge(memberId, item);

        log.info("[quarantine-check] 판정 결과 memberId={} 품목={} 분류={} → {} 과태료안내={} 조치안내={}",
                memberId, item.name(), item.category(), verdict.level().getLabel(),
                verdict.penalty() != null, verdict.action() != null);
        return QuarantineCheckResponse.of(item, verdict);
    }

    /**
     * 호출 횟수를 하나 소모한다. 상한을 넘겼으면 429로 거절하고 GMS·Bedrock을 부르지 않는다.
     *
     * 소모는 <b>이미지 검증을 통과한 뒤</b>에 한다 — 잘못된 요청(400)으로 횟수를 잃으면 안 되기 때문이다.
     */
    private void consumeQuota(Long memberId) {
        if (memberRepository.consumeItemAnalysisQuota(memberId, ITEM_ANALYSIS_LIMIT) == 0) {
            log.info("[quarantine-check] 호출 횟수 소진 memberId={} 상한={}", memberId, ITEM_ANALYSIS_LIMIT);
            throw new ItemAnalysisLimitExceededException();
        }
        log.info("[quarantine-check] 호출 횟수 소모 memberId={} 상한={}", memberId, ITEM_ANALYSIS_LIMIT);
    }

    /** 사진에서 물품을 못 찾으면 422. 호출 자체가 실패하면 500 — 어느 쪽이든 소모한 횟수는 그대로다. */
    private ItemAnalysis analyze(Long memberId, ScanImage image) {
        try {
            return itemAnalysisClient.analyze(image).orElseThrow(() -> {
                log.info("[quarantine-check] 물품 인식 실패 memberId={}", memberId);
                return new ItemNotRecognizedException();
            });
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[quarantine-check] 물품 분석 실패 memberId={}", memberId, e);
            throw new ItemAnalysisFailedException();
        }
    }

    private QuarantineVerdict judge(Long memberId, ItemAnalysis item) {
        try {
            return quarantineRegulationClient.judge(item);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[quarantine-check] 규정 검색·판정 실패 memberId={} 품목={} 분류={}",
                    memberId, item.name(), item.category(), e);
            throw new QuarantineCheckFailedException();
        }
    }
}
