package ssafy.checkmate.domain.member.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.PackedNameCount;
import ssafy.checkmate.domain.member.dto.request.MemberFavoriteItemRequest;
import ssafy.checkmate.domain.member.dto.response.FavoriteItemsResponse;
import ssafy.checkmate.domain.member.dto.response.FrequentItemsResponse;
import ssafy.checkmate.domain.member.dto.response.MemberItemBriefResponse;
import ssafy.checkmate.domain.member.dto.response.MemberItemResponse;
import ssafy.checkmate.domain.member.dto.response.MemberItemsResponse;
import ssafy.checkmate.domain.member.entity.MemberItem;
import ssafy.checkmate.domain.member.exception.InvalidItemImageException;
import ssafy.checkmate.domain.member.exception.MemberItemAccessDeniedException;
import ssafy.checkmate.domain.member.exception.MemberItemNotFoundException;
import ssafy.checkmate.domain.member.repository.MemberItemRepository;
import ssafy.checkmate.global.storage.ImageStorage;

/**
 * 마이페이지 「내 물건」 — 회원이 여행을 가로질러 담아 온 물건과 즐겨찾기.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberItemService {

    private static final int FAVORITE_PAGE_SIZE = 9;
    private static final int FREQUENT_ITEM_COUNT = 9;

    private final MemberItemRepository memberItemRepository;
    private final BagItemRepository bagItemRepository;
    private final ImageStorage imageStorage;

    /**
     * 내 물건 목록. 두 출처를 합친다.
     *  - 담은 기록({@code BagItem}, departureChecked) — 이름별 담은 횟수
     *  - 마스터({@code MemberItem}) — 즐겨찾기 여부. 담은 적이 없어도 즐겨찾기면 목록에 오른다(횟수 0)
     *
     * 조회인데 쓰기 트랜잭션인 이유: 담은 기록에는 있는데 마스터에 없는 이름을 여기서 채워 넣는다.
     * 모든 항목이 안정적인 itemId 를 가져야 즐겨찾기 토글이 id 로 동작하고, 물건을 담는 기존 코드
     * (체크리스트·스캔)를 건드리지 않고도 마스터가 자연히 채워진다. 같은 회원이 다시 불러도 이미
     * 있는 이름은 만들지 않으므로 멱등이다.
     *
     * 알고 받아들인 한계: 같은 회원이 정확히 동시에 두 번 첫 조회를 하면(예: 기기 둘) 둘 다 같은 이름을
     * 만들려다 한쪽이 유니크 제약에 걸려 500 이 난다. 다시 부르면 정상이고, 마이페이지 한 화면이 두 번
     * 동시에 뜨는 일은 드물어 재시도 로직을 넣지 않았다. 문제가 되면 REQUIRES_NEW 로 채우기만 분리한다.
     *
     * 정렬은 담은 횟수 내림차순, 같으면 이름순 — 화면의 "자주 담은 물건" 기본 순서다.
     */
    @Transactional
    public MemberItemsResponse getMyItems(Long memberId) {
        List<PackedNameCount> packed = bagItemRepository.countPackedByMember(memberId);
        Map<String, Long> countByName = new HashMap<>();
        for (PackedNameCount p : packed) {
            countByName.put(p.getName(), p.getPackedCount());
        }

        Map<String, MemberItem> master = new HashMap<>();
        for (MemberItem item : memberItemRepository.findAllByMemberId(memberId)) {
            master.put(item.getName(), item);
        }
        Map<String, String> inventoryImageByName = latestInventoryImages(memberId);

        List<MemberItem> missing = new ArrayList<>();
        for (PackedNameCount p : packed) {
            MemberItem existing = master.get(p.getName());
            if (existing == null) {
                missing.add(MemberItem.fromPacked(memberId, p.getName(), p.getCategory()));
            } else {
                existing.fillCategoryIfEmpty(p.getCategory());
            }
        }
        if (!missing.isEmpty()) {
            for (MemberItem created : memberItemRepository.saveAll(missing)) {
                master.put(created.getName(), created);
            }
        }

        List<MemberItemResponse> items = master.values().stream()
                .map(item -> MemberItemResponse.of(item, countByName.getOrDefault(item.getName(), 0L),
                        resolveImage(item, inventoryImageByName.get(item.getName()))))
                .sorted(Comparator.comparingLong(MemberItemResponse::packedCount).reversed()
                        .thenComparing(MemberItemResponse::name))
                .toList();
        return MemberItemsResponse.of(items);
    }

    /**
     * 즐겨찾기 물건(10-1번). 즐겨찾기한 내 물건을 id 내림차순으로 9개씩 커서 페이징한다.
     * 다음 페이지가 있는지는 한 개를 더 읽어 확인하고, 이미지는 마스터에 저장된 값을 그대로 쓴다.
     */
    public FavoriteItemsResponse getFavoriteItems(Long memberId, Long cursor) {
        List<MemberItem> rows = memberItemRepository.findFavoriteItems(
                memberId, cursor, PageRequest.of(0, FAVORITE_PAGE_SIZE + 1));

        boolean hasNext = rows.size() > FAVORITE_PAGE_SIZE;
        List<MemberItem> page = hasNext ? rows.subList(0, FAVORITE_PAGE_SIZE) : rows;
        return FavoriteItemsResponse.of(page, hasNext);
    }

    /**
     * 자주 담은 물건(10-2번). 담은 횟수 상위 9개를 이름과 사진으로 내려준다.
     * 순위는 사진 유무와 상관없이 담은 횟수로 먼저 정하고, 그다음에 이름마다 사진을 채운다.
     */
    public FrequentItemsResponse getFrequentItems(Long memberId) {
        List<String> names = bagItemRepository.findMostPackedNames(memberId, PageRequest.of(0, FREQUENT_ITEM_COUNT));
        if (names.isEmpty()) {
            return FrequentItemsResponse.of(List.of());
        }

        Map<String, String> imageByName = latestImageByName(memberId, names);
        List<MemberItemBriefResponse> items = names.stream()
                .map(name -> MemberItemBriefResponse.of(name, imageByName.get(name)))
                .toList();
        return FrequentItemsResponse.of(items);
    }

    /** 즐겨찾기 켜기/끄기. 본인 물건이 아니면 403, 없으면 404. 응답은 목록의 한 줄과 같은 모양. */
    @Transactional
    public MemberItemResponse setFavorite(Long memberId, Long itemId, boolean favorite) {
        MemberItem item = memberItemRepository.findById(itemId).orElseThrow(MemberItemNotFoundException::new);
        if (!item.isOwnedBy(memberId)) {
            throw new MemberItemAccessDeniedException();
        }
        item.setFavorite(favorite);
        return toResponse(memberId, item);
    }

    /**
     * 여러 물건을 한 번에 즐겨찾기로 — 가방 인벤토리의 「즐겨찾기」 가 부른다.
     *
     * id 가 아니라 이름으로 받는다. 인벤토리 물건은 카메라가 찾아준 것이라 마스터에 아직 없을 수
     * 있다 — 있으면 별만 켜고(분류가 비어 있으면 채우고) 없으면 새로 만든다. 요청 안에 같은 이름이
     * 두 번 있어도 두 번째는 첫 번째가 만든 행을 찾아 켜기만 하므로 중복 행이 생기지 않는다.
     * 응답은 갱신된 목록 전체다 — 화면이 별 하나 켜고 목록을 다시 안 불러도 되게.
     */
    @Transactional
    public MemberItemsResponse addFavorites(Long memberId, List<MemberFavoriteItemRequest> requests) {
        // 짐싸기에서 찍은 누끼는 이미 S3(BagItem.imageUrl)에 있다 — 다시 올리지 않고 URL 만 복사한다
        Map<String, String> inventoryImageByName = latestInventoryImages(memberId);
        for (MemberFavoriteItemRequest request : requests) {
            MemberItem existing = memberItemRepository.findByMemberIdAndName(memberId, request.getName()).orElse(null);
            if (existing == null) {
                existing = memberItemRepository.save(
                        MemberItem.asFavorite(memberId, request.getName(), request.getCategory()));
            } else {
                existing.setFavorite(true);
                existing.fillCategoryIfEmpty(request.getCategory());
            }
            existing.fillImageIfEmpty(inventoryImageByName.get(request.getName()));
        }
        return getMyItems(memberId);
    }

    /**
     * 물건 대표 사진 교체 — 「내 물건」 카메라 등록이 올린다. 인벤토리를 거치지 않은 누끼는 서버에 없으므로
     * 여기서 처음 저장한다. 회원 폴더 아래 items/{itemId}. 프로필·표지 사진과 같은 저장소·검증.
     */
    @Transactional
    public MemberItemResponse updateImage(Long memberId, Long itemId, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidItemImageException();
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidItemImageException();
        }
        MemberItem item = memberItemRepository.findById(itemId).orElseThrow(MemberItemNotFoundException::new);
        if (!item.isOwnedBy(memberId)) {
            throw new MemberItemAccessDeniedException();
        }
        item.updateImage(imageStorage.upload(image, memberId + "/items/" + itemId));
        return toResponse(memberId, item);
    }

    /** 이름별 최근 인벤토리 누끼 — 회원의 사진 있는 물품을 최근 순으로 훑어 이름마다 첫 것만 남긴다 */
    private Map<String, String> latestInventoryImages(Long memberId) {
        Map<String, String> byName = new HashMap<>();
        for (BagItem item : bagItemRepository.findAllByMemberIdAndImageUrlIsNotNullOrderByIdDesc(memberId)) {
            byName.putIfAbsent(item.getName(), item.getImageUrl());
        }
        return byName;
    }

    /** 지정한 이름들의 최근 인벤토리 누끼 — 사진이 하나도 없는 이름은 맵에 없다 */
    private Map<String, String> latestImageByName(Long memberId, List<String> names) {
        return bagItemRepository.findLatestImagedItemsByNames(memberId, names).stream()
                .collect(Collectors.toMap(BagItem::getName, BagItem::getImageUrl));
    }

    /** 마스터에 사진이 있으면 그것, 없으면 인벤토리 누끼, 그것도 없으면 null */
    private static String resolveImage(MemberItem item, String inventoryImage) {
        return item.getImageUrl() != null ? item.getImageUrl() : inventoryImage;
    }

    /** 한 건 응답 — 담은 횟수와 사진을 그 물건에 대해서만 다시 구한다 */
    private MemberItemResponse toResponse(Long memberId, MemberItem item) {
        long packedCount = bagItemRepository.countByMemberIdAndNameAndDepartureChecked(memberId, item.getName(), true);
        return MemberItemResponse.of(item, packedCount,
                resolveImage(item, latestInventoryImages(memberId).get(item.getName())));
    }
}
