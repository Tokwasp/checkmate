package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ssafy.checkmate.domain.checklist.dto.request.ScanItemRequest;
import ssafy.checkmate.domain.checklist.dto.response.ScanItemResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.BagItemRiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskLevelType;
import ssafy.checkmate.domain.checklist.exception.BagNotFoundException;
import ssafy.checkmate.domain.checklist.exception.InvalidRegulationTypeException;
import ssafy.checkmate.domain.checklist.exception.InvalidScanImageException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagItemRiskLevelRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.repository.RiskLevelRepository;
import ssafy.checkmate.domain.checklist.service.ScanService;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.repository.TripRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScanServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    /** 물품 마스터(ItemClassSeeder)의 보조배터리 = 전자기기 */
    private static final Long POWER_BANK_CLASS_ID = 4L;

    @Autowired
    private ScanService scanService;

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
    @DisplayName("체크리스트에 같은 이름의 안 담긴 항목이 있으면 그 항목을 담긴 상태로 바꾼다")
    void packsExistingChecklistItem() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag bag = saveBag(trip, MEMBER_ID);
        BagItem existing = bagItemRepository.save(
                BagItem.create(trip.getId(), MEMBER_ID, "보조배터리", null, 2, bag.getId()));

        // when
        ScanItemResponse response = scanService.registerItem(MEMBER_ID, request(trip, bag, "보조배터리", "WARNING"));

        // then
        assertThat(response.getItemId()).isEqualTo(existing.getId());
        assertThat(response.getName()).isEqualTo("보조배터리");

        BagItem packed = bagItemRepository.findById(existing.getId()).orElseThrow();
        assertThat(packed.isDepartureChecked()).isTrue();
        assertThat(packed.getBagId()).isEqualTo(bag.getId());
        assertThat(packed.getImageUrl()).isNotBlank();
        assertThat(packed.getCategory()).isEqualTo("전자기기");
        assertThat(packed.getQuantity()).isEqualTo(2);
        assertThat(bagItemRepository.findAllByTripId(trip.getId())).hasSize(1);
    }

    @Test
    @DisplayName("같은 이름의 안 담긴 항목이 다른 가방에 있으면 매칭하지 않고 요청받은 가방에 새로 만든다")
    void doesNotMatchItemInAnotherBag() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), MEMBER_ID));
        Bag carrier = saveBag(trip, MEMBER_ID);
        BagItem inOtherBag = bagItemRepository.save(
                BagItem.create(trip.getId(), MEMBER_ID, "보조배터리", null, 2, defaultBag.getId()));

        // when
        ScanItemResponse response =
                scanService.registerItem(MEMBER_ID, request(trip, carrier, "보조배터리", "WARNING"));

        // then
        assertThat(response.getItemId()).isNotEqualTo(inOtherBag.getId());
        BagItem untouched = bagItemRepository.findById(inOtherBag.getId()).orElseThrow();
        assertThat(untouched.isDepartureChecked()).isFalse();
        assertThat(untouched.getBagId()).isEqualTo(defaultBag.getId());

        BagItem created = bagItemRepository.findById(response.getItemId()).orElseThrow();
        assertThat(created.getBagId()).isEqualTo(carrier.getId());
        assertThat(created.isDepartureChecked()).isTrue();
        assertThat(created.getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 가방에 이미 담긴 같은 이름 물건이 있으면 새로 만들지 않고 수량만 1 늘린다")
    void increasesQuantityWhenAlreadyPackedInSameBag() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag bag = saveBag(trip, MEMBER_ID);
        BagItem alreadyPacked = bagItemRepository.save(
                BagItem.createRecognized(trip.getId(), MEMBER_ID, "보조배터리", "전자기기", bag.getId(), "already.png"));

        // when
        ScanItemResponse response = scanService.registerItem(MEMBER_ID, request(trip, bag, "보조배터리", "WARNING"));

        // then
        assertThat(response.getItemId()).isEqualTo(alreadyPacked.getId());
        assertThat(bagItemRepository.findAllByTripId(trip.getId())).hasSize(1);

        BagItem updated = bagItemRepository.findById(alreadyPacked.getId()).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(2);
        assertThat(updated.isDepartureChecked()).isTrue();
    }

    @Test
    @DisplayName("같은 이름의 담긴 물건이 다른 가방에 있으면 수량을 늘리지 않고 요청받은 가방에 새로 만든다")
    void createsNewItemWhenPackedMatchIsInAnotherBag() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), MEMBER_ID));
        Bag carrier = saveBag(trip, MEMBER_ID);
        BagItem packedInOtherBag = bagItemRepository.save(
                BagItem.createRecognized(trip.getId(), MEMBER_ID, "보조배터리", "전자기기", defaultBag.getId(), "already.png"));

        // when
        ScanItemResponse response =
                scanService.registerItem(MEMBER_ID, request(trip, carrier, "보조배터리", "WARNING"));

        // then
        assertThat(response.getItemId()).isNotEqualTo(packedInOtherBag.getId());
        assertThat(bagItemRepository.findById(packedInOtherBag.getId()).orElseThrow().getQuantity()).isEqualTo(1);

        BagItem created = bagItemRepository.findById(response.getItemId()).orElseThrow();
        assertThat(created.getBagId()).isEqualTo(carrier.getId());
        assertThat(created.getQuantity()).isEqualTo(1);
        assertThat(created.getCategory()).isEqualTo("전자기기");
    }

    @Test
    @DisplayName("물품 마스터에 없는 ID면 카테고리를 기타로 저장한다")
    void fallsBackToEtcCategory() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag bag = saveBag(trip, MEMBER_ID);
        ScanItemRequest request = request(trip, bag, "정체불명", "NONE");
        request.setItemClassId(9999L);

        // when
        ScanItemResponse response = scanService.registerItem(MEMBER_ID, request);

        // then
        assertThat(bagItemRepository.findById(response.getItemId()).orElseThrow().getCategory()).isEqualTo("기타");
    }

    @Test
    @DisplayName("인식 단계를 bag_item_risk_level에 함께 저장한다")
    void savesRiskLevel() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag bag = saveBag(trip, MEMBER_ID);

        // when
        ScanItemResponse response = scanService.registerItem(MEMBER_ID, request(trip, bag, "가위", "VIOLATION"));

        // then
        BagItemRiskLevel riskLevel = bagItemRiskLevelRepository.findByBagItemId(response.getItemId()).orElseThrow();
        RiskLevel violation = riskLevelRepository.findByLevel(RiskLevelType.VIOLATION).orElseThrow();
        assertThat(riskLevel.getRiskLevelId()).isEqualTo(violation.getId());
    }

    @Test
    @DisplayName("같은 가방에서 같은 물품을 다시 인식하면 그 행의 인식 단계를 새 값으로 갱신하고 수량도 늘어난다")
    void updatesRiskLevelOnRescan() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag bag = saveBag(trip, MEMBER_ID);
        bagItemRepository.save(BagItem.create(trip.getId(), MEMBER_ID, "보조배터리", null, 1, bag.getId()));
        ScanItemResponse first = scanService.registerItem(MEMBER_ID, request(trip, bag, "보조배터리", "NONE"));

        // when — 첫 인식으로 이미 담긴 상태이므로 두 번째 인식은 새 행을 만들지 않고 같은 행을 그대로 쓴다
        ScanItemResponse second = scanService.registerItem(MEMBER_ID, request(trip, bag, "보조배터리", "WARNING"));

        // then
        assertThat(second.getItemId()).isEqualTo(first.getItemId());
        assertThat(bagItemRepository.findById(first.getItemId()).orElseThrow().getQuantity()).isEqualTo(2);
        RiskLevel warning = riskLevelRepository.findByLevel(RiskLevelType.WARNING).orElseThrow();
        assertThat(bagItemRiskLevelRepository.findByBagItemId(first.getItemId()).orElseThrow().getRiskLevelId())
                .isEqualTo(warning.getId());
    }

    @Test
    @DisplayName("이미지가 비어 있으면 400으로 거절한다")
    void rejectsEmptyImage() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag bag = saveBag(trip, MEMBER_ID);
        ScanItemRequest request = request(trip, bag, "보조배터리", "NONE");
        request.setImage(new MockMultipartFile("image", "cutout.png", "image/png", new byte[0]));

        // when & then
        assertThatThrownBy(() -> scanService.registerItem(MEMBER_ID, request))
                .isInstanceOf(InvalidScanImageException.class);
    }

    @Test
    @DisplayName("규정 타입이 유효하지 않으면 400으로 거절한다")
    void rejectsInvalidRegulationType() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag bag = saveBag(trip, MEMBER_ID);

        // when & then
        assertThatThrownBy(() -> scanService.registerItem(MEMBER_ID, request(trip, bag, "보조배터리", "DANGER")))
                .isInstanceOf(InvalidRegulationTypeException.class);
    }

    @Test
    @DisplayName("본인의 여행이 아니면 403으로 거절한다")
    void rejectsOtherMembersTrip() {
        // given
        Trip trip = saveTrip(OTHER_MEMBER_ID);
        Bag bag = saveBag(trip, OTHER_MEMBER_ID);

        // when & then
        assertThatThrownBy(() -> scanService.registerItem(MEMBER_ID, request(trip, bag, "보조배터리", "NONE")))
                .isInstanceOf(TripAccessDeniedException.class);
    }

    @Test
    @DisplayName("다른 여행의 가방이면 404로 거절한다")
    void rejectsBagFromAnotherTrip() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Trip otherTrip = saveTrip(MEMBER_ID);
        Bag otherBag = saveBag(otherTrip, MEMBER_ID);

        // when & then
        assertThatThrownBy(() -> scanService.registerItem(MEMBER_ID, request(trip, otherBag, "보조배터리", "NONE")))
                .isInstanceOf(BagNotFoundException.class);
    }

    private ScanItemRequest request(Trip trip, Bag bag, String name, String regulationType) {
        ScanItemRequest request = new ScanItemRequest();
        request.setImage(image());
        request.setItemClassId(POWER_BANK_CLASS_ID);
        request.setName(name);
        request.setTripId(trip.getId());
        request.setBagId(bag.getId());
        request.setRegulationType(regulationType);
        return request;
    }

    private MultipartFile image() {
        return new MockMultipartFile("image", "cutout.png", "image/png", "cutout".getBytes());
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
