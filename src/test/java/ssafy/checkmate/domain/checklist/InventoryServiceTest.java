package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ssafy.checkmate.domain.checklist.TestRequestFactory.bagItemUpdateRequest;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.BagItemRiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskCheckStep;
import ssafy.checkmate.domain.checklist.entity.RiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskLevelType;
import ssafy.checkmate.domain.checklist.exception.BagItemAccessDeniedException;
import ssafy.checkmate.domain.checklist.exception.BagItemBagMismatchException;
import ssafy.checkmate.domain.checklist.exception.BagItemNotFoundException;
import ssafy.checkmate.domain.checklist.exception.BagItemUpdateEmptyException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagItemRiskLevelRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.repository.RiskLevelRepository;
import ssafy.checkmate.domain.checklist.service.InventoryService;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.repository.TripRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Autowired
    private BagItemRiskLevelRepository bagItemRiskLevelRepository;

    @Autowired
    private RiskLevelRepository riskLevelRepository;

    @Test
    @DisplayName("수량과 체크 여부를 수정한다")
    void updatesQuantityAndChecked() {
        // given
        Bag bag = saveBag(saveTrip(MEMBER_ID), MEMBER_ID);
        BagItem item = saveItem(bag, "보조배터리");

        // when
        inventoryService.updateItem(item.getId(), MEMBER_ID, bagItemUpdateRequest(bag.getId(), "보조배터리", 3, false));

        // then
        BagItem updated = bagItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(3);
        assertThat(updated.isDepartureChecked()).isFalse();
    }

    @Test
    @DisplayName("보내지 않은 필드는 기존 값을 유지한다")
    void keepsOmittedFields() {
        // given
        Bag bag = saveBag(saveTrip(MEMBER_ID), MEMBER_ID);
        BagItem item = saveItem(bag, "보조배터리");

        // when
        inventoryService.updateItem(item.getId(), MEMBER_ID, bagItemUpdateRequest(bag.getId(), "보조배터리", 5, null));

        // then
        BagItem updated = bagItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(5);
        assertThat(updated.isDepartureChecked()).isTrue();
        assertThat(updated.getImageUrl()).isEqualTo("cutout.png");
    }

    @Test
    @DisplayName("수량·체크 여부가 모두 없으면 400으로 거절한다")
    void rejectsEmptyUpdate() {
        // given
        Bag bag = saveBag(saveTrip(MEMBER_ID), MEMBER_ID);
        BagItem item = saveItem(bag, "보조배터리");

        // when & then
        assertThatThrownBy(() ->
                inventoryService.updateItem(item.getId(), MEMBER_ID, bagItemUpdateRequest(bag.getId(), "보조배터리", null, null)))
                .isInstanceOf(BagItemUpdateEmptyException.class);
    }

    @Test
    @DisplayName("요청한 가방·이름이 실제 물품과 다르면 400으로 거절한다")
    void rejectsMismatchedBagOrName() {
        // given
        Bag bag = saveBag(saveTrip(MEMBER_ID), MEMBER_ID);
        BagItem item = saveItem(bag, "보조배터리");

        // when & then
        assertThatThrownBy(() ->
                inventoryService.updateItem(item.getId(), MEMBER_ID, bagItemUpdateRequest(bag.getId(), "칫솔", 2, null)))
                .isInstanceOf(BagItemBagMismatchException.class);
        assertThatThrownBy(() ->
                inventoryService.updateItem(item.getId(), MEMBER_ID, bagItemUpdateRequest(bag.getId() + 999, "보조배터리", 2, null)))
                .isInstanceOf(BagItemBagMismatchException.class);
    }

    @Test
    @DisplayName("본인의 물품이 아니면 403으로 거절한다")
    void rejectsOtherMembersItem() {
        // given
        Bag bag = saveBag(saveTrip(OTHER_MEMBER_ID), OTHER_MEMBER_ID);
        BagItem item = saveItem(bag, "보조배터리");

        // when & then
        assertThatThrownBy(() ->
                inventoryService.updateItem(item.getId(), MEMBER_ID, bagItemUpdateRequest(bag.getId(), "보조배터리", 2, null)))
                .isInstanceOf(BagItemAccessDeniedException.class);
    }

    @Test
    @DisplayName("물품을 삭제하면 인식 단계도 함께 지운다")
    void deletesItemWithRiskLevel() {
        // given
        Bag bag = saveBag(saveTrip(MEMBER_ID), MEMBER_ID);
        BagItem item = saveItem(bag, "보조배터리");
        RiskLevel warning = riskLevelRepository.findByLevel(RiskLevelType.WARNING).orElseThrow();
        bagItemRiskLevelRepository.save(
                BagItemRiskLevel.create(item.getId(), warning.getId(), RiskCheckStep.ITEM_RECOGNITION));

        // when
        inventoryService.deleteItem(item.getId(), MEMBER_ID);

        // then
        assertThat(bagItemRepository.findById(item.getId())).isEmpty();
        assertThat(bagItemRiskLevelRepository.findByBagItemId(item.getId())).isEmpty();
    }

    @Test
    @DisplayName("없는 물품을 삭제하면 404로 거절한다")
    void rejectsDeletingMissingItem() {
        assertThatThrownBy(() -> inventoryService.deleteItem(99999L, MEMBER_ID))
                .isInstanceOf(BagItemNotFoundException.class)
                .hasMessage("존재하지 않는 인벤토리 물품입니다.");
    }

    private BagItem saveItem(Bag bag, String name) {
        return bagItemRepository.save(BagItem.createRecognized(
                bag.getTripId(), bag.getMemberId(), name, "전자기기", bag.getId(), "cutout.png"));
    }

    private Trip saveTrip(Long memberId) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .country("일본")
                .region("오사카")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .purposes(List.of("휴가"))
                .latitude(34.6937)
                .longitude(135.5023)
                .build());
    }

    private Bag saveBag(Trip trip, Long memberId) {
        return bagRepository.save(Bag.create(trip.getId(), memberId, "내 캐리어"));
    }
}
