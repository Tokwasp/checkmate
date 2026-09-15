package ssafy.checkmate.domain.checklist.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.request.BagCreateRequest;
import ssafy.checkmate.domain.checklist.dto.request.BagUpdateRequest;
import ssafy.checkmate.domain.checklist.dto.response.BagResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.BagType;
import ssafy.checkmate.domain.checklist.entity.CarriageType;
import ssafy.checkmate.domain.checklist.exception.BagAccessDeniedException;
import ssafy.checkmate.domain.checklist.exception.BagNameRequiredException;
import ssafy.checkmate.domain.checklist.exception.BagNotFoundException;
import ssafy.checkmate.domain.checklist.exception.DefaultBagNotDeletableException;
import ssafy.checkmate.domain.checklist.exception.DefaultBagNotModifiableException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BagService {

    private final BagRepository bagRepository;
    private final BagItemRepository bagItemRepository;
    private final TripRepository tripRepository;

    @Transactional
    public BagResponse createBag(Long tripId, Long memberId, BagCreateRequest request) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        if (!trip.isOwnedBy(memberId)) {
            throw new TripAccessDeniedException("본인의 여행만 수정할 수 있습니다.");
        }
        Bag bag = bagRepository.save(Bag.create(tripId, memberId, request.getName()));
        return BagResponse.of(bag);
    }

    @Transactional
    public BagResponse updateBag(Long bagId, Long memberId, BagUpdateRequest request) {
        Bag bag = bagRepository.findById(bagId).orElseThrow(BagNotFoundException::new);
        validateUpdatable(bag, memberId);

        if (request.getName() != null && request.getName().isBlank()) {
            throw new BagNameRequiredException();
        }

        CarriageType carriageType = Optional.ofNullable(request.getCarriageType())
                .map(CarriageType::parseUserInput)
                .orElse(null);

        bag.rename(request.getName());
        bag.changeCarriageType(carriageType);
        return BagResponse.of(bag);
    }

    @Transactional
    public void deleteBag(Long bagId, Long memberId) {
        Bag bag = bagRepository.findById(bagId).orElseThrow(BagNotFoundException::new);
        validateDeletable(bag, memberId);

        Bag defaultBag = ensureDefaultBag(bag.getTripId(), bag.getMemberId());
        List<BagItem> items = bagItemRepository.findAllByBagId(bagId);
        items.forEach(item -> item.moveToDefaultBag(defaultBag.getId()));

        bagRepository.delete(bag);
    }

    @Transactional
    public Bag ensureDefaultBag(Long tripId, Long memberId) {
        return bagRepository.findByTripIdAndType(tripId, BagType.NONE)
                .orElseGet(() -> bagRepository.save(Bag.createDefault(tripId, memberId)));
    }

    private void validateUpdatable(Bag bag, Long memberId) {
        if (!bag.isOwnedBy(memberId)) {
            throw new BagAccessDeniedException("본인의 가방만 수정할 수 있습니다.");
        }
        if (bag.isDefault()) {
            throw new DefaultBagNotModifiableException();
        }
    }

    private void validateDeletable(Bag bag, Long memberId) {
        if (!bag.isOwnedBy(memberId)) {
            throw new BagAccessDeniedException("본인의 가방만 삭제할 수 있습니다.");
        }
        if (bag.isDefault()) {
            throw new DefaultBagNotDeletableException();
        }
    }
}
