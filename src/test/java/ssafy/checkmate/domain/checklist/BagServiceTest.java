package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ssafy.checkmate.domain.checklist.TestRequestFactory.bagCreateRequest;
import static ssafy.checkmate.domain.checklist.TestRequestFactory.bagUpdateRequest;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.dto.response.BagResponse;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.BagType;
import ssafy.checkmate.domain.checklist.exception.BagAccessDeniedException;
import ssafy.checkmate.domain.checklist.exception.BagNotFoundException;
import ssafy.checkmate.domain.checklist.exception.DefaultBagNotDeletableException;
import ssafy.checkmate.domain.checklist.exception.DefaultBagNotModifiableException;
import ssafy.checkmate.domain.checklist.exception.InvalidCarriageTypeException;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.checklist.service.BagService;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BagServiceTest {

    @Autowired
    private BagService bagService;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Autowired
    private TripRepository tripRepository;

    @Test
    @DisplayName("가방을 생성하면 type은 CARRIER, carriageType 응답은 NONE이다")
    void createBag() {
        // given
        Trip trip = createTrip(1L);

        // when
        BagResponse response = bagService.createBag(trip.getId(), 1L, bagCreateRequest("내 캐리어"));

        // then
        assertThat(response.getName()).isEqualTo("내 캐리어");
        assertThat(response.getType()).isEqualTo("CARRIER");
        assertThat(response.getCarriageType()).isEqualTo("NONE");

        Bag saved = bagRepository.findById(response.getBagId()).orElseThrow();
        assertThat(saved.getType()).isEqualTo(BagType.CARRIER);
        assertThat(saved.getCarriageType()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 여행에 가방을 생성하면 예외가 발생한다")
    void createBagWithNoTrip() {
        // when // then
        assertThatThrownBy(() -> bagService.createBag(999L, 1L, bagCreateRequest("가방")))
                .isInstanceOf(TripNotFoundException.class)
                .hasMessage("존재하지 않는 여행입니다.");
    }

    @Test
    @DisplayName("본인의 여행이 아니면 가방을 생성할 수 없다")
    void createBagWithNotOwner() {
        // given
        Trip trip = createTrip(1L);

        // when // then
        assertThatThrownBy(() -> bagService.createBag(trip.getId(), 2L, bagCreateRequest("가방")))
                .isInstanceOf(TripAccessDeniedException.class);
    }

    @Test
    @DisplayName("가방 이름과 운송 구분을 수정한다")
    void updateBag() {
        // given
        Trip trip = createTrip(1L);
        Bag bag = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));

        // when
        BagResponse response = bagService.updateBag(bag.getId(), 1L, bagUpdateRequest("기내용 백팩", "위탁"));

        // then
        assertThat(response.getName()).isEqualTo("기내용 백팩");
        assertThat(response.getCarriageType()).isEqualTo("위탁");
    }

    @Test
    @DisplayName("carriageType이 기내/위탁이 아니면 예외가 발생한다")
    void updateBagWithInvalidCarriageType() {
        // given
        Trip trip = createTrip(1L);
        Bag bag = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));

        // when // then
        assertThatThrownBy(() -> bagService.updateBag(bag.getId(), 1L, bagUpdateRequest(null, "NONE")))
                .isInstanceOf(InvalidCarriageTypeException.class);
    }

    @Test
    @DisplayName("기본 가방은 수정할 수 없다")
    void updateDefaultBag() {
        // given
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));

        // when // then
        assertThatThrownBy(() -> bagService.updateBag(defaultBag.getId(), 1L, bagUpdateRequest("이름변경", null)))
                .isInstanceOf(DefaultBagNotModifiableException.class);
    }

    @Test
    @DisplayName("본인의 가방이 아니면 수정할 수 없다")
    void updateBagWithNotOwner() {
        // given
        Trip trip = createTrip(1L);
        Bag bag = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));

        // when // then
        assertThatThrownBy(() -> bagService.updateBag(bag.getId(), 2L, bagUpdateRequest("이름변경", null)))
                .isInstanceOf(BagAccessDeniedException.class);
    }

    @Test
    @DisplayName("가방을 삭제하면 담겨 있던 항목이 기본 가방으로 옮겨지고 출국 전 체크가 해제된다. 귀국 전 체크는 유지된다")
    void deleteBag() {
        // given
        Trip trip = createTrip(1L);
        Bag bag = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));
        BagItem item = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, bag.getId()));
        item.update(null, null, null, true, null); // departureChecked = true 로 만들어둔다

        // when
        bagService.deleteBag(bag.getId(), 1L);

        // then
        assertThat(bagRepository.findById(bag.getId())).isEmpty();

        Bag defaultBag = bagRepository.findByTripIdAndType(trip.getId(), BagType.NONE).orElseThrow();
        BagItem moved = bagItemRepository.findById(item.getId()).orElseThrow();
        assertThat(moved.getBagId()).isEqualTo(defaultBag.getId());
        assertThat(moved.isDepartureChecked()).isFalse();
        assertThat(moved.isReturnChecked()).isFalse();
    }

    @Test
    @DisplayName("기본 가방은 삭제할 수 없다")
    void deleteDefaultBag() {
        // given
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));

        // when // then
        assertThatThrownBy(() -> bagService.deleteBag(defaultBag.getId(), 1L))
                .isInstanceOf(DefaultBagNotDeletableException.class);
    }

    @Test
    @DisplayName("존재하지 않는 가방을 수정/삭제하면 예외가 발생한다")
    void bagNotFound() {
        // when // then
        assertThatThrownBy(() -> bagService.updateBag(999L, 1L, bagUpdateRequest("x", null)))
                .isInstanceOf(BagNotFoundException.class);
        assertThatThrownBy(() -> bagService.deleteBag(999L, 1L))
                .isInstanceOf(BagNotFoundException.class);
    }

    @Test
    @DisplayName("기본 가방이 없으면 새로 만들고, 있으면 그 가방을 그대로 반환한다")
    void ensureDefaultBag() {
        // given
        Trip trip = createTrip(1L);

        // when
        Bag first = bagService.ensureDefaultBag(trip.getId(), 1L);
        Bag second = bagService.ensureDefaultBag(trip.getId(), 1L);

        // then
        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.isDefault()).isTrue();
        assertThat(first.getName()).isEqualTo("NONE");
        assertThat(bagRepository.findAll()).hasSize(1);
    }

    private Trip createTrip(Long memberId) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .destination("오사카")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .purposes(List.of("휴가"))
                .build());
    }
}
