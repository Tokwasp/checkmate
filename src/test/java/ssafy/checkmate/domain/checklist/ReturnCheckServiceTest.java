package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckBagResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckItemResponse;
import ssafy.checkmate.domain.checklist.dto.response.ReturnCheckResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.CarriageType;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.service.ReturnCheckService;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;

/**
 * 귀가 전 점검 현황 조회(31번) 서비스 테스트.
 *
 * 외부 호출이 없어 모킹할 협력자가 없다 — 실제 리포지토리를 그대로 태운다.
 * {@code returnChecked}를 켜는 도메인 메서드는 체크 API(다음 티켓)가 생길 때 만들기로 해서,
 * 여기서는 리플렉션으로 상태만 만들어 둔다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReturnCheckServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Autowired
    private ReturnCheckService returnCheckService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Test
    @DisplayName("여행의 나라·지역·기간·귀국 항공사를 그대로 내려준다")
    void getReturnCheck() {
        // given
        Trip trip = saveTrip(MEMBER_ID);

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getTripId()).isEqualTo(trip.getId());
        assertThat(response.getCountry()).isEqualTo("일본");
        assertThat(response.getRegion()).isEqualTo("도쿄");
        assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(response.getArrivalAirline()).isEqualTo("대한항공");
    }

    @Test
    @DisplayName("항공편이 없는 여행이면 귀국 항공사는 null이다")
    void getReturnCheckWithoutFlight() {
        // given
        Trip trip = saveTripWithoutFlight(MEMBER_ID);

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getArrivalAirline()).isNull();
    }

    @Test
    @DisplayName("가방에 담은 물품만 점검 목록에 올린다 — 아직 안 담은 체크리스트 항목은 빠진다")
    void getReturnCheckOnlyPackedItems() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        savePackedItem(trip, carrier, "여권");
        saveUnpackedItem(trip, carrier, "우산");

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(itemNamesOf(response, 0)).containsExactly("여권");
    }

    @Test
    @DisplayName("bags의 맨 앞은 합산 가방 '전체'이고, 모든 물품을 한 번 더 담는다")
    void getReturnCheckPutsAllBagFirst() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        savePackedItem(trip, carrier, "여권");
        savePackedItem(trip, cabin, "보조배터리");

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        ReturnCheckBagResponse all = response.getBags().get(0);
        assertThat(all.getBagId()).isNull();
        assertThat(all.getName()).isEqualTo("전체");
        assertThat(all.getBagType()).isNull();
        assertThat(all.getCarriageType()).isNull();
        assertThat(all.getTotalCount()).isEqualTo(2);
        assertThat(itemNamesOf(response, 0)).containsExactly("여권", "보조배터리");
        assertThat(response.getBags()).hasSize(3);
    }

    @Test
    @DisplayName("담긴 물품이 하나도 없는 가방은 bags에서 뺀다")
    void getReturnCheckExcludesEmptyBags() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        saveBag(trip, "백팩", CarriageType.CABIN);
        savePackedItem(trip, carrier, "여권");

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getBags())
                .extracting(ReturnCheckBagResponse::getName)
                .containsExactly("전체", "캐리어");
    }

    @Test
    @DisplayName("기본 가방(NONE)도 담긴 물품이 있으면 다른 가방과 똑같이 내려준다")
    void getReturnCheckIncludesDefaultBag() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), MEMBER_ID));
        savePackedItem(trip, defaultBag, "여권");

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        ReturnCheckBagResponse bag = response.getBags().get(1);
        assertThat(bag.getBagId()).isEqualTo(defaultBag.getId());
        assertThat(bag.getName()).isEqualTo("NONE");
        assertThat(bag.getBagType()).isEqualTo("NONE");
        assertThat(bag.getCarriageType()).isEqualTo("NONE");
        assertThat(bag.getTotalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("개수는 수량 합이 아니라 물품 행 수로 센다")
    void getReturnCheckCountsRowsNotQuantity() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        savePackedItem(trip, carrier, "양말", 5, null, false);

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getBags().get(1).getTotalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("귀가 전 체크된 물품만 returnCheckedCount로 세고, 최상위 합계는 '전체' 가방과 같다")
    void getReturnCheckCountsReturnChecked() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        savePackedItem(trip, carrier, "여권", 1, null, true);
        savePackedItem(trip, carrier, "충전기", 1, null, false);
        savePackedItem(trip, cabin, "보조배터리", 1, null, true);

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getReturnCheckedCount()).isEqualTo(2);
        assertThat(response.getBags().get(0).getTotalCount()).isEqualTo(response.getTotalCount());
        assertThat(response.getBags().get(0).getReturnCheckedCount()).isEqualTo(response.getReturnCheckedCount());
        assertThat(response.getBags().get(1).getReturnCheckedCount()).isEqualTo(1);
        assertThat(response.getBags().get(2).getReturnCheckedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("가방은 '전체' 다음 bagId 오름차순, 물품은 itemId 오름차순으로 내려준다 — 체크 여부는 순서를 바꾸지 않는다")
    void getReturnCheckSortsBagsAndItems() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag carrier = saveBag(trip, "캐리어", CarriageType.CHECKED);
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem checkedFirst = savePackedItem(trip, carrier, "여권", 1, null, true);
        BagItem uncheckedSecond = savePackedItem(trip, carrier, "충전기", 1, null, false);
        savePackedItem(trip, cabin, "보조배터리");

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getBags())
                .extracting(ReturnCheckBagResponse::getBagId)
                .containsExactly(null, carrier.getId(), cabin.getId());
        assertThat(response.getBags().get(1).getItems())
                .extracting(ReturnCheckItemResponse::getItemId)
                .containsExactly(checkedFirst.getId(), uncheckedSecond.getId());
    }

    @Test
    @DisplayName("담은 물품이 하나도 없으면 '전체' 가방만 빈 채로 내려준다")
    void getReturnCheckWithNoPackedItems() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        saveBag(trip, "캐리어", CarriageType.CHECKED);

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getReturnCheckedCount()).isZero();
        assertThat(response.getBags()).hasSize(1);
        assertThat(response.getBags().get(0).getName()).isEqualTo("전체");
        assertThat(response.getBags().get(0).getItems()).isEmpty();
    }

    @Test
    @DisplayName("물품에는 이미지·귀가 전 체크 여부와 함께 담긴 가방을 꼬리표로 붙여준다")
    void getReturnCheckItemFields() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Bag cabin = saveBag(trip, "기내백", CarriageType.CABIN);
        BagItem battery = savePackedItem(trip, cabin, "보조배터리", 1, "https://s3.example.com/1/a.png", true);
        BagItem passport = savePackedItem(trip, cabin, "여권");

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        List<ReturnCheckItemResponse> items = response.getBags().get(0).getItems();
        assertThat(items.get(0))
                .extracting(ReturnCheckItemResponse::getItemId, ReturnCheckItemResponse::getName,
                        ReturnCheckItemResponse::getImageUrl, ReturnCheckItemResponse::isReturnChecked,
                        ReturnCheckItemResponse::getBagId, ReturnCheckItemResponse::getBagName)
                .containsExactly(battery.getId(), "보조배터리", "https://s3.example.com/1/a.png", true,
                        cabin.getId(), "기내백");
        assertThat(items.get(1))
                .extracting(ReturnCheckItemResponse::getItemId, ReturnCheckItemResponse::getImageUrl,
                        ReturnCheckItemResponse::isReturnChecked)
                .containsExactly(passport.getId(), null, false);
    }

    @Test
    @DisplayName("다른 여행에 담은 물품은 섞이지 않는다")
    void getReturnCheckExcludesOtherTrips() {
        // given
        Trip trip = saveTrip(MEMBER_ID);
        Trip otherTrip = saveTrip(MEMBER_ID);
        savePackedItem(trip, saveBag(trip, "캐리어", CarriageType.CHECKED), "여권");
        savePackedItem(otherTrip, saveBag(otherTrip, "캐리어", CarriageType.CHECKED), "수영복");

        // when
        ReturnCheckResponse response = returnCheckService.getReturnCheck(trip.getId(), MEMBER_ID);

        // then
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(itemNamesOf(response, 0)).containsExactly("여권");
    }

    @Test
    @DisplayName("존재하지 않는 여행이면 404로 거절한다")
    void getReturnCheckMissingTrip() {
        // when // then
        assertThatThrownBy(() -> returnCheckService.getReturnCheck(999_999L, MEMBER_ID))
                .isInstanceOf(TripNotFoundException.class)
                .hasMessage("존재하지 않는 여행입니다.");
    }

    @Test
    @DisplayName("본인 여행이 아니면 403으로 거절한다")
    void getReturnCheckOtherMembersTrip() {
        // given
        Trip trip = saveTrip(MEMBER_ID);

        // when // then
        assertThatThrownBy(() -> returnCheckService.getReturnCheck(trip.getId(), OTHER_MEMBER_ID))
                .isInstanceOf(TripAccessDeniedException.class)
                .hasMessage("본인의 여행만 조회할 수 있습니다.");
    }

    private List<String> itemNamesOf(ReturnCheckResponse response, int bagIndex) {
        return response.getBags().get(bagIndex).getItems().stream()
                .map(ReturnCheckItemResponse::getName)
                .toList();
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

    private Trip saveTripWithoutFlight(Long memberId) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .country("일본")
                .region("도쿄")
                .startDate(LocalDate.of(2026, 9, 16))
                .endDate(LocalDate.of(2026, 9, 20))
                .purposes(List.of("휴가"))
                .hasFlight(false)
                .latitude(35.6895)
                .longitude(139.6917)
                .build());
    }

    private Bag saveBag(Trip trip, String name, CarriageType carriageType) {
        Bag bag = Bag.create(trip.getId(), MEMBER_ID, name);
        bag.changeCarriageType(carriageType);
        return bagRepository.save(bag);
    }

    private BagItem savePackedItem(Trip trip, Bag bag, String name) {
        return savePackedItem(trip, bag, name, 1, null, false);
    }

    private BagItem savePackedItem(Trip trip, Bag bag, String name, int quantity, String imageUrl,
                                   boolean returnChecked) {
        BagItem item = BagItem.create(trip.getId(), MEMBER_ID, name, "기타", quantity, bag.getId());
        item.packInto(bag.getId(), imageUrl, "기타");
        ReflectionTestUtils.setField(item, "returnChecked", returnChecked);
        return bagItemRepository.saveAndFlush(item);
    }

    private BagItem saveUnpackedItem(Trip trip, Bag bag, String name) {
        return bagItemRepository.saveAndFlush(
                BagItem.create(trip.getId(), MEMBER_ID, name, "기타", 1, bag.getId()));
    }
}
