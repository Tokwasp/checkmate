package ssafy.checkmate.domain.member.dto.response;

import java.util.List;

/** 「내 물건」 목록 — 다른 목록 응답(ChecklistItemsResponse 등)과 같이 items 로 감싼다. */
public record MemberItemsResponse(List<MemberItemResponse> items) {

    public static MemberItemsResponse of(List<MemberItemResponse> items) {
        return new MemberItemsResponse(items);
    }
}
