package ssafy.checkmate.domain.checklist.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *
 * 로그는 EC2에서 흐름을 따라갈 수 있도록 {@code [scan]} 접두사로 남긴다. 운영 로그 레벨이
 * INFO라 정상 흐름은 info, 물품 마스터 미등록처럼 짚어볼 일은 warn으로 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScanService {

    /** 물품 마스터에 없는 ID가 들어왔을 때 쓰는 기본 카테고리 — 인식 때문에 짐 싸기가 막히지 않도록 한다. */
    private static final String FALLBACK_CATEGORY = "기타";
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

        String category = resolveCategory(request.getItemClassId());
        String imageUrl = imageStorage.upload(request.getImage(), String.valueOf(memberId));
        log.info("[scan] 이미지 업로드 완료 tripId={} bagId={} category={} imageUrl={}",
                trip.getId(), bag.getId(), category, imageUrl);

        BagItem item = packItem(trip, memberId, bag, request.getName(), category, imageUrl);
        applyRiskLevel(item.getId(), riskLevel);

        return ScanItemResponse.of(item);
    }

    /** 물품 마스터에서 카테고리를 찾는다. 없으면 기본 카테고리로 대체하고 warn으로 남긴다(마스터 보강 신호). */
    private String resolveCategory(Long itemClassId) {
        return itemClassRepository.findById(itemClassId)
                .map(ItemClass::getCategory)
                .orElseGet(() -> {
                    log.warn("[scan] 물품 마스터에 없는 itemClassId={} — 카테고리를 {}로 대체한다",
                            itemClassId, FALLBACK_CATEGORY);
                    return FALLBACK_CATEGORY;
                });
    }

    /**
     * 이름이 같은 물건을 찾아 넷 중 하나로 처리한다 (2026-09-20 개정).
     * 1) 요청받은 가방에 아직 안 담긴 항목이 있으면 → 그 항목을 담긴 상태로 바꾼다(체크리스트 대조, 수량 유지).
     * 2) 요청받은 가방에 이미 담긴 항목이 있으면 → 같은 물건을 하나 더 인식한 것으로 보아 수량만 +1.
     * 3) 다른 가방(주로 기본 가방 NONE)에 아직 안 담긴 항목이 있으면 → 새로 만들지 않고 그 항목을
     *    요청받은 가방으로 옮겨 담는다. 체크리스트를 처음 만들면 모든 항목이 NONE에 있고, 인식은
     *    항상 가방을 고른 채로 하기 때문에 이 경우가 사실상 기본 경로다.
     * 4) 어디에도 없으면 → 그 가방에 수량 1짜리 새 물품을 만든다.
     */
    private BagItem packItem(Trip trip, Long memberId, Bag bag, String name, String category, String imageUrl) {
        Optional<BagItem> uncheckedHere = bagItemRepository
                .findFirstByTripIdAndBagIdAndNameAndDepartureCheckedFalseOrderByIdAsc(trip.getId(), bag.getId(), name);
        if (uncheckedHere.isPresent()) {
            BagItem existing = uncheckedHere.get();
            existing.packInto(bag.getId(), imageUrl, category);
            log.info("[scan] 체크리스트 항목 자동 체크 tripId={} itemId={} name={} bagId={}",
                    trip.getId(), existing.getId(), name, bag.getId());
            return existing;
        }

        Optional<BagItem> alreadyPacked = bagItemRepository
                .findFirstByTripIdAndBagIdAndNameAndDepartureCheckedTrueOrderByIdAsc(trip.getId(), bag.getId(), name);
        if (alreadyPacked.isPresent()) {
            BagItem existing = alreadyPacked.get();
            existing.increaseQuantity(1);
            log.info("[scan] 같은 가방에 이미 담긴 동명 물건 — 수량만 증가 tripId={} itemId={} name={} bagId={} quantity={}",
                    trip.getId(), existing.getId(), name, bag.getId(), existing.getQuantity());
            return existing;
        }

        Optional<BagItem> uncheckedElsewhere = bagItemRepository
                .findFirstByTripIdAndNameAndDepartureCheckedFalseOrderByIdAsc(trip.getId(), name);
        if (uncheckedElsewhere.isPresent()) {
            BagItem existing = uncheckedElsewhere.get();
            Long from = existing.getBagId();
            existing.packInto(bag.getId(), imageUrl, category);
            log.info("[scan] 다른 가방의 체크리스트 항목을 옮겨 담음 tripId={} itemId={} name={} bagId={}->{}",
                    trip.getId(), existing.getId(), name, from, bag.getId());
            return existing;
        }

        TripStep before = trip.getCurrentStep();
        trip.advanceStepAtLeast(TripStep.ITEM_RECOGNITION);
        BagItem created = bagItemRepository.save(BagItem.createRecognized(
                trip.getId(), memberId, name, category, bag.getId(), imageUrl));
        log.info("[scan] 체크리스트에 없던 물품을 새로 담음 tripId={} itemId={} name={} bagId={} step={}->{}",
                trip.getId(), created.getId(), name, bag.getId(), before, trip.getCurrentStep());
        return created;
    }

    /** 규정 타입 문자열을 검증하고, 위험(주의·금지)일 때만 그에 대응하는 RiskLevel 마스터 행을 찾아 돌려준다. */
    private Optional<RiskLevel> resolveRiskLevel(String regulationType) {
        if (NO_RISK_REGULATION_TYPE.equals(regulationType)) {
            return Optional.empty();
        }
        RiskLevelType type = parseRegulationType(regulationType);
        return Optional.of(riskLevelRepository.findByLevel(type).orElseThrow(() -> {
            log.warn("[scan] RiskLevel 마스터에 {} 행이 없다 — RiskLevelInitializer 실행 여부를 확인할 것", type);
            return new InvalidRegulationTypeException();
        }));
    }

    private RiskLevelType parseRegulationType(String regulationType) {
        try {
            return RiskLevelType.valueOf(regulationType);
        } catch (IllegalArgumentException e) {
            log.warn("[scan] 알 수 없는 regulationType={} — NONE/WARNING/VIOLATION만 받는다", regulationType);
            throw new InvalidRegulationTypeException();
        }
    }

    private void applyRiskLevel(Long bagItemId, Optional<RiskLevel> riskLevel) {
        if (riskLevel.isEmpty()) {
            bagItemRiskLevelRepository.deleteByBagItemId(bagItemId);
            log.info("[scan] 위험도 없음(NONE) — 기존 위험도 행 제거 itemId={}", bagItemId);
            return;
        }
        RiskLevel level = riskLevel.get();
        saveRiskLevel(bagItemId, level.getId());
        log.info("[scan] 위험도 저장 itemId={} level={} step={}",
                bagItemId, level.getLevel(), RiskCheckStep.ITEM_RECOGNITION);
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
            log.warn("[scan] 이미지가 비어 있는 요청 — 등록을 거절한다");
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
