package ssafy.checkmate.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.member.dto.response.MemberItemResponse;
import ssafy.checkmate.domain.member.dto.response.MemberItemsResponse;
import ssafy.checkmate.domain.member.entity.MemberItem;
import ssafy.checkmate.domain.member.repository.MemberItemRepository;
import ssafy.checkmate.domain.member.service.MemberItemService;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.repository.TripRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberItemServiceTest {

    @Autowired
    private MemberItemService memberItemService;

    @Autowired
    private MemberItemRepository memberItemRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Test
    @DisplayName("내 물건 목록은 여행을 가로질러 담은 횟수를 세고, 즐겨찾기만 한 물건도 포함하며, 남의 것과 안 담은 것은 뺀다")
    void getMyItems() {
        // given — 회원 1 의 여행 둘, 회원 2 의 여행 하나
        Long me = 1L;
        Long other = 2L;
        Trip tokyo = createTrip(me, "도쿄");
        Trip osaka = createTrip(me, "오사카");
        Trip otherTrip = createTrip(other, "다낭");
        Long tokyoBag = bagRepository.save(Bag.createDefault(tokyo.getId(), me)).getId();
        Long osakaBag = bagRepository.save(Bag.createDefault(osaka.getId(), me)).getId();
        Long otherBag = bagRepository.save(Bag.createDefault(otherTrip.getId(), other)).getId();

        // 여권 — 두 여행에 모두 담음 → 2
        pack(tokyo.getId(), me, "여권", "서류", tokyoBag);
        pack(osaka.getId(), me, "여권", "서류", osakaBag);
        // 충전기 — 한 번 담음 → 1
        pack(tokyo.getId(), me, "충전기", "전자기기", tokyoBag);
        // 우산 — 체크리스트에만 있고 담지는 않음 → 목록에 없어야 한다
        bagItemRepository.save(BagItem.create(tokyo.getId(), me, "우산", "생활용품", 1, tokyoBag));
        // 남의 여권 — 회원 2 → 내 목록에 섞이면 안 된다
        pack(otherTrip.getId(), other, "여권", "서류", otherBag);
        // 선글라스 — 담은 적은 없지만 즐겨찾기로 올려 둔 물건 → 횟수 0 으로 포함
        memberItemRepository.save(MemberItem.asFavorite(me, "선글라스", null));

        // when
        MemberItemsResponse response = memberItemService.getMyItems(me);

        // then — 담은 횟수 내림차순, 같으면 이름순
        assertThat(response.items())
                .extracting(MemberItemResponse::name, MemberItemResponse::packedCount, MemberItemResponse::isFavorite)
                .containsExactly(
                        tuple("여권", 2L, false),
                        tuple("충전기", 1L, false),
                        tuple("선글라스", 0L, true));
        // 분류는 담은 기록에서 따라온다
        assertThat(response.items().get(0).category()).isEqualTo("서류");
        // 모든 항목에 id 가 있다 — 즐겨찾기 토글이 id 로 동작해야 하므로
        assertThat(response.items()).allSatisfy(item -> assertThat(item.itemId()).isNotNull());
    }

    @Test
    @DisplayName("담은 기록에만 있던 물건은 조회하면서 마스터에 채워지고, 다시 조회해도 중복 생성되지 않는다")
    void getMyItemsFillsMasterIdempotently() {
        // given
        Long me = 1L;
        Trip trip = createTrip(me, "도쿄");
        Long bag = bagRepository.save(Bag.createDefault(trip.getId(), me)).getId();
        pack(trip.getId(), me, "여권", "서류", bag);
        pack(trip.getId(), me, "충전기", "전자기기", bag);
        assertThat(memberItemRepository.findAllByMemberId(me)).isEmpty();

        // when — 두 번 부른다
        MemberItemsResponse first = memberItemService.getMyItems(me);
        MemberItemsResponse second = memberItemService.getMyItems(me);

        // then — 마스터 행은 이름마다 하나씩, 두 번째 호출에도 늘지 않는다
        List<MemberItem> master = memberItemRepository.findAllByMemberId(me);
        assertThat(master).extracting(MemberItem::getName).containsExactlyInAnyOrder("여권", "충전기");
        assertThat(second.items()).hasSameSizeAs(first.items()).hasSize(2);
        // 같은 이름은 두 호출에서 같은 id 를 돌려준다
        assertThat(second.items()).extracting(MemberItemResponse::itemId)
                .containsExactlyElementsOf(first.items().stream().map(MemberItemResponse::itemId).toList());
    }

    @Test
    @DisplayName("담은 것도 즐겨찾기도 없으면 빈 목록이다")
    void getMyItemsEmpty() {
        assertThat(memberItemService.getMyItems(99L).items()).isEmpty();
    }

    /** 물품을 만들고 곧바로 「담기」 체크한다 — departureChecked=true */
    private void pack(Long tripId, Long memberId, String name, String category, Long bagId) {
        BagItem item = bagItemRepository.save(BagItem.create(tripId, memberId, name, category, 1, bagId));
        item.update(null, null, null, true, null);
    }

    private Trip createTrip(Long memberId, String region) {
        LocalDate today = LocalDate.now();
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .country("일본")
                .region(region)
                .startDate(today.minusDays(10))
                .endDate(today.minusDays(7))
                .purposes(List.of("휴가"))
                .latitude(35.6762)
                .longitude(139.6503)
                .build());
    }
}
