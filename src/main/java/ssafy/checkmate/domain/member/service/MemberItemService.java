package ssafy.checkmate.domain.member.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.PackedNameCount;
import ssafy.checkmate.domain.member.dto.request.MemberFavoriteItemRequest;
import ssafy.checkmate.domain.member.dto.response.MemberItemResponse;
import ssafy.checkmate.domain.member.dto.response.MemberItemsResponse;
import ssafy.checkmate.domain.member.entity.MemberItem;
import ssafy.checkmate.domain.member.exception.MemberItemAccessDeniedException;
import ssafy.checkmate.domain.member.exception.MemberItemNotFoundException;
import ssafy.checkmate.domain.member.repository.MemberItemRepository;

/**
 * 마이페이지 「내 물건」 — 회원이 여행을 가로질러 담아 온 물건과 즐겨찾기.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberItemService {

    private final MemberItemRepository memberItemRepository;
    private final BagItemRepository bagItemRepository;

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
                .map(item -> MemberItemResponse.of(item, countByName.getOrDefault(item.getName(), 0L)))
                .sorted(Comparator.comparingLong(MemberItemResponse::packedCount).reversed()
                        .thenComparing(MemberItemResponse::name))
                .toList();
        return MemberItemsResponse.of(items);
    }

    /** 즐겨찾기 켜기/끄기. 본인 물건이 아니면 403, 없으면 404. 응답은 목록의 한 줄과 같은 모양. */
    @Transactional
    public MemberItemResponse setFavorite(Long memberId, Long itemId, boolean favorite) {
        MemberItem item = memberItemRepository.findById(itemId).orElseThrow(MemberItemNotFoundException::new);
        if (!item.isOwnedBy(memberId)) {
            throw new MemberItemAccessDeniedException();
        }
        item.setFavorite(favorite);
        long packedCount = bagItemRepository.countByMemberIdAndNameAndDepartureChecked(memberId, item.getName(), true);
        return MemberItemResponse.of(item, packedCount);
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
        for (MemberFavoriteItemRequest request : requests) {
            MemberItem existing = memberItemRepository.findByMemberIdAndName(memberId, request.getName()).orElse(null);
            if (existing == null) {
                memberItemRepository.save(MemberItem.asFavorite(memberId, request.getName(), request.getCategory()));
            } else {
                existing.setFavorite(true);
                existing.fillCategoryIfEmpty(request.getCategory());
            }
        }
        return getMyItems(memberId);
    }
}
