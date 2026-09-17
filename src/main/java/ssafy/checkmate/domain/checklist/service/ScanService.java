package ssafy.checkmate.domain.checklist.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ssafy.checkmate.domain.checklist.dto.request.ScanItemRequest;
import ssafy.checkmate.domain.checklist.dto.response.ScanItemResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.BagItemRiskLevel;
import ssafy.checkmate.domain.checklist.entity.ItemClass;
import ssafy.checkmate.domain.checklist.entity.RiskCheckStep;
import ssafy.checkmate.domain.checklist.entity.RiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskLevelType;
import ssafy.checkmate.domain.checklist.exception.BagAccessDeniedException;
import ssafy.checkmate.domain.checklist.exception.BagNotFoundException;
import ssafy.checkmate.domain.checklist.exception.InvalidRegulationTypeException;
import ssafy.checkmate.domain.checklist.exception.InvalidScanImageException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagItemRiskLevelRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.repository.ItemClassRepository;
import ssafy.checkmate.domain.checklist.repository.RiskLevelRepository;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.entity.TripStep;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.storage.ImageStorage;

/**
 * 짐 싸기(물건 인식) 26번. 온디바이스에서 인식한 물품을 가방에 담고 체크리스트를 자동으로 체크한다.
 *
 * 체크리스트 항목과 인벤토리 물품은 같은 BagItem 한 행이라, "체크리스트 대조"는 별도 연결
 * 테이블을 두는 게 아니라 그 행의 departureChecked/bagId/imageUrl을 갱신하는 동작이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScanService {

    /** 물품 마스터에 없는 ID가 들어왔을 때 쓰는 기본 카테고리 — 인식 때문에 짐 싸기가 막히지 않도록 한다. */
    private static final String FALLBACK_CATEGORY = "기타";
    private static final String IMAGE_DIRECTORY = "inventory";
    /** 온디바이스 판정 중 위험도가 없다는 뜻 — RiskLevel 마스터에는 없고, 행을 두지 않는 것으로 표현한다. */
    private static final String NO_RISK_REGULATION_TYPE = "NONE";

    private final BagItemRepository bagItemRepository;
    private final BagItemRiskLevelRepository bagItemRiskLevelRepository;
    private final BagRepository bagRepository;
    private final TripRepository tripRepository;
    private final ItemClassRepository itemClassRepository;
    private final RiskLevelRepository riskLevelRepository;
    private final ImageStorage imageStorage;

    @Transactional
    public ScanItemResponse registerItem(Long memberId, ScanItemRequest request) {
        validateImage(request.getImage());
        Optional<RiskLevel> riskLevel = resolveRiskLevel(request.getRegulationType());

        Trip trip = findTripOwnedBy(request.getTripId(), memberId);
        Bag bag = findBagOwnedBy(request.getBagId(), memberId, trip.getId());

        String category = itemClassRepository.findById(request.getItemClassId())
                .map(ItemClass::getCategory)
                .orElse(FALLBACK_CATEGORY);
        String imageUrl = imageStorage.upload(request.getImage(), IMAGE_DIRECTORY);

        BagItem item = packItem(trip, memberId, bag, request.getName(), category, imageUrl);
        applyRiskLevel(item.getId(), riskLevel);

        return ScanItemResponse.of(item);
    }

    /**
     * 체크리스트에 이름이 같고 아직 안 담긴 항목이 있으면 그것을 담긴 상태로 바꾸고,
     * 없으면 담긴 상태의 물품을 새로 만든다.
     */
    private BagItem packItem(Trip trip, Long memberId, Bag bag, String name, String category, String imageUrl) {
        return bagItemRepository.findFirstByTripIdAndNameAndDepartureCheckedFalseOrderByIdAsc(trip.getId(), name)
                .map(existing -> {
                    existing.packInto(bag.getId(), imageUrl, category);
                    return existing;
                })
                .orElseGet(() -> {
                    trip.advanceStepAtLeast(TripStep.ITEM_RECOGNITION);
                    return bagItemRepository.save(BagItem.createRecognized(
                            trip.getId(), memberId, name, category, bag.getId(), imageUrl));
                });
    }

    /** 규정 타입 문자열을 검증하고, 위험(주의·금지)일 때만 그에 대응하는 RiskLevel 마스터 행을 찾아 돌려준다. */
    private Optional<RiskLevel> resolveRiskLevel(String regulationType) {
        if (NO_RISK_REGULATION_TYPE.equals(regulationType)) {
            return Optional.empty();
        }
        RiskLevelType type = parseRegulationType(regulationType);
        return Optional.of(riskLevelRepository.findByLevel(type).orElseThrow(InvalidRegulationTypeException::new));
    }

    private RiskLevelType parseRegulationType(String regulationType) {
        try {
            return RiskLevelType.valueOf(regulationType);
        } catch (IllegalArgumentException e) {
            throw new InvalidRegulationTypeException();
        }
    }

    private void applyRiskLevel(Long bagItemId, Optional<RiskLevel> riskLevel) {
        if (riskLevel.isEmpty()) {
            bagItemRiskLevelRepository.deleteByBagItemId(bagItemId);
            return;
        }
        saveRiskLevel(bagItemId, riskLevel.get().getId());
    }

    private void saveRiskLevel(Long bagItemId, Long riskLevelId) {
        bagItemRiskLevelRepository.findByBagItemId(bagItemId)
                .ifPresentOrElse(
                        existing -> existing.changeRiskLevel(riskLevelId),
                        () -> bagItemRiskLevelRepository.save(
                                BagItemRiskLevel.create(bagItemId, riskLevelId, RiskCheckStep.ITEM_RECOGNITION)));
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidScanImageException();
        }
    }

    private Trip findTripOwnedBy(Long tripId, Long memberId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        if (!trip.isOwnedBy(memberId)) {
            throw new TripAccessDeniedException("본인의 여행만 수정할 수 있습니다.");
        }
        return trip;
    }

    private Bag findBagOwnedBy(Long bagId, Long memberId, Long tripId) {
        Bag bag = bagRepository.findById(bagId).orElseThrow(BagNotFoundException::new);
        if (!bag.getTripId().equals(tripId)) {
            throw new BagNotFoundException();
        }
        if (!bag.isOwnedBy(memberId)) {
            throw new BagAccessDeniedException("본인의 가방에만 물품을 담을 수 있습니다.");
        }
        return bag;
    }
}
