package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.request.ReturnCheckItemCheckRequest;
import ssafy.checkmate.domain.checklist.dto.request.ReturnCheckScanRequest;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckFoundItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckItemCheckResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckItemsResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckMissingItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckScanResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.CarriageType;
import ssafy.checkmate.domain.checklist.exception.BagItemAccessDeniedException;
import ssafy.checkmate.domain.checklist.exception.BagItemNotFoundException;
import ssafy.checkmate.domain.checklist.exception.NotReturnCheckTargetException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.service.ReturnCheckService;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;

/**
 * 카메라로 확인(32·33·33-2번) 서비스 테스트.
 *
 * 31번(가방별 점검 목록)은 {@code ReturnCheckServiceTest}가 따로 본다 — 한 클래스에
 * 20개 넘게 쌓이면 읽기 어렵다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReturnCheckScanServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Autowired
    private ReturnCheckService returnCheckService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    /* ── 32. 확인 현황 조회 ─────────────────────────────── */

    @Test
    @DisplayName("체크 여부로 가른 두 목록과 개수 두 개를 내려준다")
    void getItems() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveCheckedItem(trip, carrier, "여권", null, LocalDateTime.of(2026, 9, 20, 10, 0));
        saveUncheckedItem(trip, cabin, "우산");
        saveUncheckedItem(trip, carrier, "선글라스");

        // when
        ReturnCheckItemsResponse response = returnCheckService.getItems(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getCheckedCount()).isEqualTo(1);
        assertThat(response.getUncheckedCount()).isEqualTo(2);
        assertThat(response.getCheckedItems())
                .extracting(ReturnCheckFoundItemResponse::getName)
                .containsExactly("여권");
        assertThat(response.getUncheckedItems())
                .extracting(ReturnCheckMissingItemResponse::getName)
                .containsExactly("우산", "선글라스");
    }

    @Test
    @DisplayName("가방에 담지 않은 물품은 두 목록 어디에도 올리지 않는다")
    void getItemsOnlyPackedItems() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        saveUncheckedItem(trip, carrier, "선글라스");
        saveUnpackedItem(trip, carrier, "안 담은 우산");

        // when
        ReturnCheckItemsResponse response = returnCheckService.getItems(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getUncheckedCount()).isEqualTo(1);
        assertThat(response.getUncheckedItems())
                .extracting(ReturnCheckMissingItemResponse::getName)
                .containsExactly("선글라스");
    }

    @Test
    @DisplayName("두 목록 모두 줄마다 가방명을 붙여준다")
    void getItemsWithBagName() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        saveCheckedItem(trip, carrier, "여권", null, LocalDateTime.of(2026, 9, 20, 10, 0));
        saveUncheckedItem(trip, cabin, "우산");

        // when
        ReturnCheckItemsResponse response = returnCheckService.getItems(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getCheckedItems().get(0).getBagName()).isEqualTo("캐리어");
        assertThat(response.getUncheckedItems().get(0).getBagName()).isEqualTo("기내백");
    }

    @Test
    @DisplayName("확인된 목록에는 이미지를 주고 체크 여부는 true다")
    void getItemsCheckedFields() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem passport = saveCheckedItem(trip, carrier, "여권", "https://s3.example.com/1/a.png",
                LocalDateTime.of(2026, 9, 20, 10, 0));

        // when
        ReturnCheckItemsResponse response = returnCheckService.getItems(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getCheckedItems().get(0))
                .extracting(ReturnCheckFoundItemResponse::getItemId, ReturnCheckFoundItemResponse::getName,
                        ReturnCheckFoundItemResponse::getImageUrl, ReturnCheckFoundItemResponse::isReturnChecked,
                        ReturnCheckFoundItemResponse::getBagName)
                .containsExactly(passport.getId(), "여권", "https://s3.example.com/1/a.png", true, "캐리어");
    }

    @Test
    @DisplayName("안 보임 목록의 체크 여부는 false다")
    void getItemsUncheckedFields() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem sunglasses = saveUncheckedItem(trip, carrier, "선글라스");

        // when
        ReturnCheckItemsResponse response = returnCheckService.getItems(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getUncheckedItems().get(0))
                .extracting(ReturnCheckMissingItemResponse::getItemId, ReturnCheckMissingItemResponse::getName,
                        ReturnCheckMissingItemResponse::isReturnChecked, ReturnCheckMissingItemResponse::getBagName)
                .containsExactly(sunglasses.getId(), "선글라스", false, "캐리어");
    }

    @Test
    @DisplayName("확인된 목록은 가장 최근에 체크된 것이 맨 위다")
    void getItemsSortsCheckedByRecency() {
        // given — 만들어진 순서와 체크된 순서를 일부러 반대로 둔다
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        saveCheckedItem(trip, carrier, "먼저 만들고 나중에 체크", null, LocalDateTime.of(2026, 9, 20, 12, 0));
        saveCheckedItem(trip, carrier, "나중에 만들고 먼저 체크", null, LocalDateTime.of(2026, 9, 20, 10, 0));

        // when
        ReturnCheckItemsResponse response = returnCheckService.getItems(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getCheckedItems())
                .extracting(ReturnCheckFoundItemResponse::getName)
                .containsExactly("먼저 만들고 나중에 체크", "나중에 만들고 먼저 체크");
    }

    @Test
    @DisplayName("안 보임 목록은 itemId 오름차순이다 — 누르는 동안 순서가 흔들리면 안 된다")
    void getItemsSortsUncheckedById() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem first = saveUncheckedItem(trip, carrier, "우산");
        BagItem second = saveUncheckedItem(trip, carrier, "선글라스");

        // when
        ReturnCheckItemsResponse response = returnCheckService.getItems(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getUncheckedItems())
                .extracting(ReturnCheckMissingItemResponse::getItemId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    @DisplayName("담은 물품이 없으면 두 목록이 비고 개수는 0이다")
    void getItemsWithNoPackedItems() {
        // given
        Trip trip = saveTrip(MEMBER_ID);

        // when
        ReturnCheckItemsResponse response = returnCheckService.getItems(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getCheckedCount()).isZero();
        assertThat(response.getUncheckedCount()).isZero();
        assertThat(response.getCheckedItems()).isEmpty();
        assertThat(response.getUncheckedItems()).isEmpty();
    }

    @Test
    @DisplayName("다른 여행의 물품은 섞이지 않는다")
    void getItemsExcludesOtherTrips() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Trip otherTrip = saveTrip(MEMBER_ID);
        saveUncheckedItem(trip, saveBag(trip, "캐리어", CarriageType.CHECKED), "선글라스");
        saveUncheckedItem(otherTrip, saveBag(otherTrip, "캐리어", CarriageType.CHECKED), "수영복");

        // when
        ReturnCheckItemsResponse response = returnCheckService.getItems(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getUncheckedItems())
                .extracting(ReturnCheckMissingItemResponse::getName)
                .containsExactly("선글라스");
    }

    @Test
    @DisplayName("존재하지 않는 여행이면 404로 거절한다")
    void getItemsMissingTrip() {
        // when // then
        assertThatThrownBy(() -> returnCheckService.getItems(999_999L, MEMBER_ID))
                .isInstanceOf(TripNotFoundException.class)
                .hasMessage("존재하지 않는 여행입니다.");
    }

    @Test
    @DisplayName("본인 여행이 아니면 403으로 거절한다")
    void getItemsOtherMembersTrip() {
        // given
        Trip trip = saveTrip(MEMBER_ID);

        // when // then
        assertThatThrownBy(() -> returnCheckService.getItems(trip.getId(), OTHER_MEMBER_ID))
                .isInstanceOf(TripAccessDeniedException.class)
                .hasMessage("본인의 여행만 조회할 수 있습니다.");
    }

    /* ── 33. 인식한 물품 체크 ───────────────────────────── */

    @Test
    @DisplayName("인식한 이름과 같은 미확인 물품을 체크하고 남은 개수를 돌려준다")
    void scan() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem passport = saveUncheckedItem(trip, carrier, "여권");
        saveUncheckedItem(trip, carrier, "선글라스");

        // when
        ReturnCheckScanResponse response = returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("여권"));

        // then
        assertThat(response.isMatched()).isTrue();
        assertThat(response.getUncheckedCount()).isEqualTo(1);
        assertThat(response.getItem())
                .extracting(ReturnCheckFoundItemResponse::getItemId, ReturnCheckFoundItemResponse::getName,
                        ReturnCheckFoundItemResponse::isReturnChecked, ReturnCheckFoundItemResponse::getBagName)
                .containsExactly(passport.getId(), "여권", true, "캐리어");
        assertThat(bagItemRepository.findById(passport.getId()).orElseThrow().isReturnChecked()).isTrue();
    }

    @Test
    @DisplayName("체크해도 담김 여부(departureChecked)는 건드리지 않는다")
    void scanKeepsDepartureChecked() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem passport = saveUncheckedItem(trip, carrier, "여권");

        // when
        returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("여권"));

        // then
        assertThat(bagItemRepository.findById(passport.getId()).orElseThrow().isDepartureChecked()).isTrue();
    }

    @Test
    @DisplayName("이름이 같은 미확인 물품이 여러 개면 가장 먼저 만들어진 하나만 체크한다")
    void scanChecksOnlyOneOfDuplicates() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem first = saveUncheckedItem(trip, carrier, "우산");
        BagItem second = saveUncheckedItem(trip, carrier, "우산");

        // when
        ReturnCheckScanResponse response = returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("우산"));

        // then
        assertThat(response.getItem().getItemId()).isEqualTo(first.getId());
        assertThat(bagItemRepository.findById(second.getId()).orElseThrow().isReturnChecked()).isFalse();
        assertThat(response.getUncheckedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 이름을 두 번 인식하면 두 개가 차례로 체크된다 — 멱등이 아니다")
    void scanTwiceChecksTwo() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem first = saveUncheckedItem(trip, carrier, "우산");
        BagItem second = saveUncheckedItem(trip, carrier, "우산");

        // when
        returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("우산"));
        ReturnCheckScanResponse secondScan = returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("우산"));

        // then
        assertThat(secondScan.getItem().getItemId()).isEqualTo(second.getId());
        assertThat(secondScan.getUncheckedCount()).isZero();
        assertThat(bagItemRepository.findById(first.getId()).orElseThrow().isReturnChecked()).isTrue();
    }

    @Test
    @DisplayName("가방을 가리지 않고 찾는다 — 다른 가방에 있어도 체크한다")
    void scanIgnoresBag() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        saveBag(trip, "캐리어", CarriageType.CHECKED);
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem umbrella = saveUncheckedItem(trip, cabin, "우산");

        // when
        ReturnCheckScanResponse response = returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("우산"));

        // then
        assertThat(response.getItem().getItemId()).isEqualTo(umbrella.getId());
        assertThat(response.getItem().getBagName()).isEqualTo("기내백");
    }

    @Test
    @DisplayName("이름이 부분만 같으면 체크하지 않는다")
    void scanRequiresExactName() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem shirt = saveUncheckedItem(trip, carrier, "반팔 티");

        // when
        ReturnCheckScanResponse response = returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("반팔티"));

        // then
        assertThat(response.isMatched()).isFalse();
        assertThat(response.getItem()).isNull();
        assertThat(bagItemRepository.findById(shirt.getId()).orElseThrow().isReturnChecked()).isFalse();
    }

    @Test
    @DisplayName("그 이름 물품이 여행에 없으면 200으로 못 찾았다고 알려준다")
    void scanWithUnknownName() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        saveUncheckedItem(trip, carrier, "선글라스");

        // when
        ReturnCheckScanResponse response = returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("노트북"));

        // then
        assertThat(response.isMatched()).isFalse();
        assertThat(response.getItem()).isNull();
        assertThat(response.getUncheckedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 이름이 이미 전부 체크됐으면 못 찾았다고 알려준다")
    void scanWhenAllAlreadyChecked() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        saveCheckedItem(trip, carrier, "여권", null, LocalDateTime.of(2026, 9, 20, 10, 0));

        // when
        ReturnCheckScanResponse response = returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("여권"));

        // then
        assertThat(response.isMatched()).isFalse();
        assertThat(response.getUncheckedCount()).isZero();
    }

    @Test
    @DisplayName("담지 않은 물품은 이름이 같아도 체크하지 않는다")
    void scanIgnoresUnpackedItems() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem unpacked = saveUnpackedItem(trip, carrier, "우산");

        // when
        ReturnCheckScanResponse response = returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("우산"));

        // then
        assertThat(response.isMatched()).isFalse();
        assertThat(bagItemRepository.findById(unpacked.getId()).orElseThrow().isReturnChecked()).isFalse();
    }

    @Test
    @DisplayName("다른 여행의 같은 이름 물품은 건드리지 않는다")
    void scanExcludesOtherTrips() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Trip otherTrip = saveTrip(MEMBER_ID);
        BagItem otherUmbrella =
                saveUncheckedItem(otherTrip, saveBag(otherTrip, "캐리어", CarriageType.CHECKED), "우산");
        saveBag(trip, "캐리어", CarriageType.CHECKED);

        // when
        ReturnCheckScanResponse response = returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("우산"));

        // then
        assertThat(response.isMatched()).isFalse();
        assertThat(bagItemRepository.findById(otherUmbrella.getId()).orElseThrow().isReturnChecked()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 여행이면 404로 거절한다")
    void scanMissingTrip() {
        // when // then
        assertThatThrownBy(() -> returnCheckService.scan(999_999L, MEMBER_ID, scanRequest("여권")))
                .isInstanceOf(TripNotFoundException.class)
                .hasMessage("존재하지 않는 여행입니다.");
    }

    @Test
    @DisplayName("본인 여행이 아니면 403으로 거절한다")
    void scanOtherMembersTrip() {
        // given
        Trip trip = saveTrip(MEMBER_ID);

        // when // then
        assertThatThrownBy(() -> returnCheckService.scan(trip.getId(), OTHER_MEMBER_ID, scanRequest("여권")))
                .isInstanceOf(TripAccessDeniedException.class)
                .hasMessage("본인의 여행만 수정할 수 있습니다.");
    }

    /* ── 33-2. 손으로 체크·해제 ────────────────────────── */

    @Test
    @DisplayName("미확인 물품을 체크하면 남은 개수가 하나 줄고 그 줄을 돌려준다")
    void checkItem() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem passport = saveUncheckedItem(trip, carrier, "여권");
        saveUncheckedItem(trip, carrier, "선글라스");

        // when
        ReturnCheckItemCheckResponse response =
                returnCheckService.checkItem(trip.getId(), MEMBER_ID, passport.getId(), checkRequest(true));

        // then
        assertThat(response.getUncheckedCount()).isEqualTo(1);
        assertThat(response.getItem())
                .extracting(ReturnCheckFoundItemResponse::getItemId, ReturnCheckFoundItemResponse::getName,
                        ReturnCheckFoundItemResponse::isReturnChecked, ReturnCheckFoundItemResponse::getBagName)
                .containsExactly(passport.getId(), "여권", true, "캐리어");
        assertThat(bagItemRepository.findById(passport.getId()).orElseThrow().isReturnChecked()).isTrue();
    }

    @Test
    @DisplayName("체크된 물품을 해제하면 다시 안 보임으로 돌아간다")
    void uncheckItem() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem passport = saveCheckedItem(trip, carrier, "여권", null, LocalDateTime.of(2026, 9, 20, 10, 0));

        // when
        ReturnCheckItemCheckResponse response =
                returnCheckService.checkItem(trip.getId(), MEMBER_ID, passport.getId(), checkRequest(false));

        // then
        assertThat(response.getUncheckedCount()).isEqualTo(1);
        assertThat(response.getItem().isReturnChecked()).isFalse();
        assertThat(bagItemRepository.findById(passport.getId()).orElseThrow().isReturnChecked()).isFalse();
    }

    @Test
    @DisplayName("카메라가 체크한 줄도 그대로 해제된다 — 체크 수단을 가리지 않는다")
    void uncheckItemCheckedByCamera() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem umbrella = saveUncheckedItem(trip, carrier, "우산");
        returnCheckService.scan(trip.getId(), MEMBER_ID, scanRequest("우산"));

        // when
        ReturnCheckItemCheckResponse response =
                returnCheckService.checkItem(trip.getId(), MEMBER_ID, umbrella.getId(), checkRequest(false));

        // then
        assertThat(response.getItem().isReturnChecked()).isFalse();
        assertThat(bagItemRepository.findById(umbrella.getId()).orElseThrow().isReturnChecked()).isFalse();
    }

    @Test
    @DisplayName("같은 값을 두 번 보내도 결과가 같다 — 33번과 달리 멱등이다")
    void checkItemIsIdempotent() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem passport = saveUncheckedItem(trip, carrier, "여권");
        saveUncheckedItem(trip, carrier, "선글라스");

        // when
        returnCheckService.checkItem(trip.getId(), MEMBER_ID, passport.getId(), checkRequest(true));
        ReturnCheckItemCheckResponse second =
                returnCheckService.checkItem(trip.getId(), MEMBER_ID, passport.getId(), checkRequest(true));

        // then
        assertThat(second.getUncheckedCount()).isEqualTo(1);
        assertThat(second.getItem().isReturnChecked()).isTrue();
    }

    @Test
    @DisplayName("체크해도 담김 여부(departureChecked)는 건드리지 않는다")
    void checkItemKeepsDepartureChecked() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem passport = saveUncheckedItem(trip, carrier, "여권");

        // when
        returnCheckService.checkItem(trip.getId(), MEMBER_ID, passport.getId(), checkRequest(true));

        // then
        assertThat(bagItemRepository.findById(passport.getId()).orElseThrow().isDepartureChecked()).isTrue();
    }

    @Test
    @DisplayName("담지 않은 물품은 점검 대상이 아니라 400으로 거절한다")
    void checkItemRejectsUnpackedItem() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem unpacked = saveUnpackedItem(trip, carrier, "우산");

        // when // then
        assertThatThrownBy(() ->
                returnCheckService.checkItem(trip.getId(), MEMBER_ID, unpacked.getId(), checkRequest(true)))
                .isInstanceOf(NotReturnCheckTargetException.class)
                .hasMessage("점검 대상 물품이 아닙니다.");
        assertThat(bagItemRepository.findById(unpacked.getId()).orElseThrow().isReturnChecked()).isFalse();
    }

    @Test
    @DisplayName("다른 여행의 물품이면 404로 거절한다")
    void checkItemFromOtherTrip() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Trip otherTrip = saveTrip(MEMBER_ID);
        BagItem otherItem =
                saveUncheckedItem(otherTrip, saveBag(otherTrip, "캐리어", CarriageType.CHECKED), "수영복");

        // when // then
        assertThatThrownBy(() ->
                returnCheckService.checkItem(trip.getId(), MEMBER_ID, otherItem.getId(), checkRequest(true)))
                .isInstanceOf(BagItemNotFoundException.class)
                .hasMessage("존재하지 않는 물품입니다.");
    }

    @Test
    @DisplayName("남의 물품이면 403으로 거절한다")
    void checkItemOfOtherMember() {
        // given — 여행은 내 것인데 물품 주인만 남인 상태(정상 흐름으로는 나오지 않는다)
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem item = BagItem.create(trip.getId(), OTHER_MEMBER_ID, "여권", "기타", 1, carrier.getId());
        item.packInto(carrier.getId(), null, "기타");
        BagItem saved = bagItemRepository.saveAndFlush(item);

        // when // then
        assertThatThrownBy(() ->
                returnCheckService.checkItem(trip.getId(), MEMBER_ID, saved.getId(), checkRequest(true)))
                .isInstanceOf(BagItemAccessDeniedException.class)
                .hasMessage("본인의 물품만 수정할 수 있습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 물품이면 404로 거절한다")
    void checkItemMissingItem() {
        // given
        Trip trip = saveTrip(MEMBER_ID);

        // when // then
        assertThatThrownBy(() ->
                returnCheckService.checkItem(trip.getId(), MEMBER_ID, 999_999L, checkRequest(true)))
                .isInstanceOf(BagItemNotFoundException.class)
                .hasMessage("존재하지 않는 물품입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 여행이면 404로 거절한다")
    void checkItemMissingTrip() {
        // when // then
        assertThatThrownBy(() ->
                returnCheckService.checkItem(999_999L, MEMBER_ID, 1L, checkRequest(true)))
                .isInstanceOf(TripNotFoundException.class)
                .hasMessage("존재하지 않는 여행입니다.");
    }

    @Test
    @DisplayName("본인 여행이 아니면 403으로 거절한다")
    void checkItemOtherMembersTrip() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem passport = saveUncheckedItem(trip, carrier, "여권");

        // when // then
        assertThatThrownBy(() ->
                returnCheckService.checkItem(trip.getId(), OTHER_MEMBER_ID, passport.getId(), checkRequest(true)))
                .isInstanceOf(TripAccessDeniedException.class)
                .hasMessage("본인의 여행만 수정할 수 있습니다.");
    }

    private ReturnCheckItemCheckRequest checkRequest(Boolean returnChecked) {
        return TestRequestFactory.returnCheckItemCheckRequest(returnChecked);
    }

    private ReturnCheckScanRequest scanRequest(String name) {
        return TestRequestFactory.returnCheckScanRequest(name);
    }

    private Trip saveTrip(Long memberId) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .country("일본")
                .region("도쿄")
                .startDate(LocalDate.of(2026, 9, 16))
                .endDate(LocalDate.of(2026, 9, 20))
                .purposes(List.of("휴가"))
                .hasFlight(true)
                .departureAirline("아시아나항공")
                .arrivalAirline("대한항공")
                .latitude(35.6895)
                .longitude(139.6917)
                .build());
    }

    private Bag saveBag(Trip trip, String name, CarriageType carriageType) {
        Bag bag = Bag.create(trip.getId(), MEMBER_ID, name);
        bag.changeCarriageType(carriageType);
        return bagRepository.save(bag);
    }

    /** 가방에 담긴(departureChecked) 미확인 물품. */
    private BagItem saveUncheckedItem(Trip trip, Bag bag, String name) {
        BagItem item = BagItem.create(trip.getId(), MEMBER_ID, name, "기타", 1, bag.getId());
        item.packInto(bag.getId(), null, "기타");
        return bagItemRepository.saveAndFlush(item);
    }

    /** 아직 가방에 담지 않은 체크리스트 항목. */
    private BagItem saveUnpackedItem(Trip trip, Bag bag, String name) {
        return bagItemRepository.saveAndFlush(
                BagItem.create(trip.getId(), MEMBER_ID, name, "기타", 1, bag.getId()));
    }

    /**
     * 귀가 전 체크까지 끝난 물품.
     *
     * {@code @LastModifiedDate}가 flush 때 현재 시각으로 덮어써서 필드에 값을 넣어도 소용이
     * 없다. JPQL 벌크 업데이트는 감사·더티 체킹을 건너뛰므로 그걸로 updatedAt까지 박는다.
     */
    private BagItem saveCheckedItem(Trip trip, Bag bag, String name, String imageUrl, LocalDateTime checkedAt) {
        BagItem item = BagItem.create(trip.getId(), MEMBER_ID, name, "기타", 1, bag.getId());
        item.packInto(bag.getId(), imageUrl, "기타");
        BagItem saved = bagItemRepository.saveAndFlush(item);

        entityManager.createQuery(
                        "UPDATE BagItem b SET b.returnChecked = true, b.updatedAt = :checkedAt WHERE b.id = :id")
                .setParameter("checkedAt", checkedAt)
                .setParameter("id", saved.getId())
                .executeUpdate();
        entityManager.clear();
        return saved;
    }
}
