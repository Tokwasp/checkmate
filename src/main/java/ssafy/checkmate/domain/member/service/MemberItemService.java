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
import ssafy.checkmate.domain.member.dto.response.MemberItemResponse;
import ssafy.checkmate.domain.member.dto.response.MemberItemsResponse;
import ssafy.checkmate.domain.member.entity.MemberItem;
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
}
