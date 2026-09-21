package ssafy.checkmate.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import tools.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.member.dto.request.MemberFavoriteItemRequest;
import ssafy.checkmate.domain.member.dto.response.MemberItemResponse;
import ssafy.checkmate.domain.member.dto.response.MemberItemsResponse;
import ssafy.checkmate.domain.member.entity.MemberItem;
import ssafy.checkmate.domain.member.exception.InvalidItemImageException;
import ssafy.checkmate.domain.member.exception.MemberItemAccessDeniedException;
import ssafy.checkmate.domain.member.exception.MemberItemNotFoundException;
import ssafy.checkmate.domain.member.repository.MemberItemRepository;
import ssafy.checkmate.domain.member.service.MemberItemService;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.storage.ImageStorage;

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

    /** 실제 저장소(로컬 파일)에 쓰지 않도록 흉내 낸다 */
    @MockitoBean
    private ImageStorage imageStorage;

    @Test
    @DisplayName("짐싸기에서 찍은 누끼가 있는 물건을 즐겨찾기하면 그 사진 URL 이 마스터로 복사되고, 안 한 물건도 목록에선 누끼로 보인다")
    void addFavoritesCopiesInventoryCutout() {
        Long me = 1L;
        Trip trip = createTrip(me, "도쿄");
        Long bag = bagRepository.save(Bag.createDefault(trip.getId(), me)).getId();
        // 스캔으로 담긴 물품 — 누끼가 S3(여기선 URL 문자열) 에 있다
        bagItemRepository.save(BagItem.createRecognized(trip.getId(), me, "여권", "서류", bag, "https://s3/1/passport-old.png"));
        bagItemRepository.save(BagItem.createRecognized(trip.getId(), me, "여권", "서류", bag, "https://s3/1/passport-new.png"));
        bagItemRepository.save(BagItem.createRecognized(trip.getId(), me, "충전기", "전자기기", bag, "https://s3/1/charger.png"));

        MemberItemsResponse response = memberItemService.addFavorites(me, List.of(favorite("여권", null)));

        // 즐겨찾기한 여권: 가장 최근 누끼가 마스터에 복사됨
        assertThat(memberItemRepository.findByMemberIdAndName(me, "여권").orElseThrow().getImageUrl())
                .isEqualTo("https://s3/1/passport-new.png");
        // 즐겨찾기 안 한 충전기: 마스터엔 사진이 없어도 목록 응답은 인벤토리 누끼로 폴백
        assertThat(response.items())
                .extracting(MemberItemResponse::name, MemberItemResponse::imageUrl)
                .containsExactlyInAnyOrder(
                        tuple("여권", "https://s3/1/passport-new.png"),
                        tuple("충전기", "https://s3/1/charger.png"));
        assertThat(memberItemRepository.findByMemberIdAndName(me, "충전기").orElseThrow().getImageUrl()).isNull();
    }

    @Test
    @DisplayName("물건 사진을 올리면 회원/물건 폴더에 저장되고 응답·마스터에 반영된다. 무효 파일·남의 물건은 거절")
    void updateImage() {
        Long me = 1L;
        MemberItem mine = memberItemRepository.save(MemberItem.asFavorite(me, "이어폰", "전자기기"));
        MemberItem others = memberItemRepository.save(MemberItem.asFavorite(2L, "남의 이어폰", null));
        given(imageStorage.upload(any(), eq(me + "/items/" + mine.getId()))).willReturn("https://s3/1/items/earbuds.png");

        MemberItemResponse response = memberItemService.updateImage(me, mine.getId(),
                new MockMultipartFile("image", "cutout.png", "image/png", "png".getBytes()));

        assertThat(response.imageUrl()).isEqualTo("https://s3/1/items/earbuds.png");
        assertThat(memberItemRepository.findById(mine.getId()).orElseThrow().getImageUrl()).isEqualTo("https://s3/1/items/earbuds.png");
        assertThatThrownBy(() -> memberItemService.updateImage(me, mine.getId(), null))
                .isInstanceOf(InvalidItemImageException.class);
        assertThatThrownBy(() -> memberItemService.updateImage(me, mine.getId(),
                new MockMultipartFile("image", "note.txt", "text/plain", "x".getBytes())))
                .isInstanceOf(InvalidItemImageException.class);
        assertThatThrownBy(() -> memberItemService.updateImage(me, others.getId(),
                new MockMultipartFile("image", "c.png", "image/png", "png".getBytes())))
                .isInstanceOf(MemberItemAccessDeniedException.class);
    }

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

    @Test
    @DisplayName("즐겨찾기를 켜고 끄면 그 물건 한 줄이 담은 횟수와 함께 돌아온다")
    void setFavorite() {
        // given — 여권을 한 번 담아 마스터에 올라오게 한다
        Long me = 1L;
        Trip trip = createTrip(me, "도쿄");
        Long bag = bagRepository.save(Bag.createDefault(trip.getId(), me)).getId();
        pack(trip.getId(), me, "여권", "서류", bag);
        Long passportId = memberItemService.getMyItems(me).items().get(0).itemId();

        // when
        MemberItemResponse on = memberItemService.setFavorite(me, passportId, true);
        MemberItemResponse off = memberItemService.setFavorite(me, passportId, false);

        // then
        assertThat(on.isFavorite()).isTrue();
        assertThat(on.name()).isEqualTo("여권");
        assertThat(on.packedCount()).isEqualTo(1L);
        assertThat(off.isFavorite()).isFalse();
        assertThat(memberItemRepository.findById(passportId).orElseThrow().isFavorite()).isFalse();
    }

    @Test
    @DisplayName("없는 물건이면 404, 남의 물건이면 403 이다")
    void setFavoriteRejects() {
        MemberItem others = memberItemRepository.save(MemberItem.asFavorite(2L, "남의 여권", "서류"));

        assertThatThrownBy(() -> memberItemService.setFavorite(1L, 999_999L, true))
                .isInstanceOf(MemberItemNotFoundException.class);
        assertThatThrownBy(() -> memberItemService.setFavorite(1L, others.getId(), true))
                .isInstanceOf(MemberItemAccessDeniedException.class);
        // 거절됐으니 남의 물건은 그대로다
        assertThat(memberItemRepository.findById(others.getId()).orElseThrow().isFavorite()).isTrue();
    }

    @Test
    @DisplayName("이름으로 여러 개 즐겨찾기하면 있는 것은 별만 켜고 없는 것은 새로 만들며, 중복 이름도 한 행이다")
    void addFavorites() {
        // given — 여권은 담은 적이 있어 마스터에 있고(분류 없이), 선글라스는 처음 보는 이름
        Long me = 1L;
        Trip trip = createTrip(me, "도쿄");
        Long bag = bagRepository.save(Bag.createDefault(trip.getId(), me)).getId();
        pack(trip.getId(), me, "여권", null, bag);
        memberItemService.getMyItems(me);
        assertThat(memberItemRepository.findByMemberIdAndName(me, "여권").orElseThrow().getCategory()).isNull();

        // when — 여권(분류 채워서) · 선글라스 · 선글라스(중복)
        MemberItemsResponse response = memberItemService.addFavorites(me, List.of(
                favorite("여권", "서류"),
                favorite("선글라스", "패션"),
                favorite("선글라스", null)));

        // then
        assertThat(response.items())
                .extracting(MemberItemResponse::name, MemberItemResponse::isFavorite, MemberItemResponse::packedCount)
                .containsExactly(
                        tuple("여권", true, 1L),
                        tuple("선글라스", true, 0L));
        // 있던 물건의 빈 분류가 채워졌고, 중복 이름은 한 행뿐이다
        assertThat(memberItemRepository.findByMemberIdAndName(me, "여권").orElseThrow().getCategory()).isEqualTo("서류");
        assertThat(memberItemRepository.findAllByMemberId(me)).hasSize(2);
    }

    /** 요청 DTO 는 setter 가 없어 Jackson 으로 만든다 (TestRequestFactory 와 같은 방식) */
    private static MemberFavoriteItemRequest favorite(String name, String category) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("category", category);
        return new ObjectMapper().convertValue(map, MemberFavoriteItemRequest.class);
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
