package ssafy.checkmate.domain.member.dto.response;

import java.util.List;
import ssafy.checkmate.domain.member.entity.MemberItem;

/**
 * 즐겨찾기 물건 한 페이지 — 커서 기반 무한스크롤용.
 * 각 물건에 id 를 내려주지 않으므로 다음 페이지 커서는 응답 단위의 nextCursor 로 받는다.
 */
public record FavoriteItemsResponse(
        List<MemberItemBriefResponse> items,
        boolean hasNext,
        /** 다음 페이지 요청에 cursor 로 보낼 값(이번 페이지 마지막 물건의 id). 다음 페이지가 없으면 null */
        Long nextCursor
) {
    public static FavoriteItemsResponse of(List<MemberItem> page, boolean hasNext) {
        List<MemberItemBriefResponse> items = page.stream()
                .map(MemberItemBriefResponse::of)
                .toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new FavoriteItemsResponse(items, hasNext, nextCursor);
    }
}
