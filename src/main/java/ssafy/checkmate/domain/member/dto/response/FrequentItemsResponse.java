package ssafy.checkmate.domain.member.dto.response;

import java.util.List;

/** 자주 담은 물건 — 담은 횟수 상위 물건. 페이징이 없어 items 만 담는다. */
public record FrequentItemsResponse(List<MemberItemBriefResponse> items) {

    public static FrequentItemsResponse of(List<MemberItemBriefResponse> items) {
        return new FrequentItemsResponse(items);
    }
}
