package ssafy.checkmate.domain.member.dto.response;

import ssafy.checkmate.domain.member.entity.MemberItem;

/** 마이페이지 「내 물건」 한 줄. */
public record MemberItemResponse(
        Long itemId,
        String name,
        /** 물품 분류 — BagItem 과 같은 문자열(예: "서류"). 모르면 null */
        String category,
        boolean isFavorite,
        /** 지금까지 담은 횟수. 즐겨찾기만 하고 아직 안 담았으면 0 */
        long packedCount,
        /** 대표 사진(누끼). 마스터에 없으면 같은 이름의 최근 인벤토리 누끼, 그것도 없으면 null */
        String imageUrl
) {
    public static MemberItemResponse of(MemberItem item, long packedCount, String imageUrl) {
        return new MemberItemResponse(item.getId(), item.getName(), item.getCategory(), item.isFavorite(), packedCount,
                imageUrl);
    }
}
