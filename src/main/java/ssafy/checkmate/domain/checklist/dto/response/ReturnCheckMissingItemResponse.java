package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;

/**
 * 아직 확인하지 못한 물품 한 줄 (카메라 화면의 '안 보임' 탭).
 *
 * 이미지를 두지 않는다 — 아직 찾지 못한 물건이라 이름과 가방명이면 충분하고,
 * 한 타입에 imageUrl을 두고 null을 채우면 "빈 값"과 "줄 필요 없음"이 구분되지 않는다.
 */
@Getter
public class ReturnCheckMissingItemResponse {

    private final Long itemId;
    private final String name;
    /** 이 목록에서는 항상 false. */
    private final boolean returnChecked;
    private final String bagName;

    private ReturnCheckMissingItemResponse(Long itemId, String name, boolean returnChecked, String bagName) {
        this.itemId = itemId;
        this.name = name;
        this.returnChecked = returnChecked;
        this.bagName = bagName;
    }

    public static ReturnCheckMissingItemResponse of(BagItem item, Bag bag) {
        return new ReturnCheckMissingItemResponse(
                item.getId(), item.getName(), item.isReturnChecked(), bag == null ? null : bag.getName());
    }
}
