package ssafy.checkmate.domain.member.dto.response;

import ssafy.checkmate.domain.member.entity.MemberItem;

/** 「내 물건」 즐겨찾기·자주 담은 물건 영역의 물건 한 칸 — 이미지와 이름만 담는다. */
public record MemberItemBriefResponse(
        String name,
        /** 대표 사진 URL. 없으면 null — 화면이 기본 아이콘으로 대신한다 */
        String imageUrl
) {
    public static MemberItemBriefResponse of(String name, String imageUrl) {
        return new MemberItemBriefResponse(name, imageUrl);
    }

    /** 즐겨찾기는 마스터(MemberItem)에 저장된 사진을 그대로 쓴다. 인벤토리 누끼로 대체하지 않는다. */
    public static MemberItemBriefResponse of(MemberItem item) {
        return new MemberItemBriefResponse(item.getName(), item.getImageUrl());
    }
}
