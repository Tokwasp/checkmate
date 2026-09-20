package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;

/**
 * 귀가 전 점검(31번) 목록의 물품 한 칸.
 *
 * 담긴 가방을 함께 내려주는 이유는 '전체' 탭에서 "어느 가방 것"인지 꼬리표로 쓰기 때문이다.
 */
@Getter
public class ReturnCheckItemResponse {

    private final Long itemId;
    private final String name;
    /** 카메라로 인식해 담은 물품만 값이 있고, 손으로 체크한 물품은 null. */
    private final String imageUrl;
    private final boolean returnChecked;
    private final Long bagId;
    private final String bagName;

    private ReturnCheckItemResponse(Long itemId, String name, String imageUrl, boolean returnChecked,
            Long bagId, String bagName) {
        this.itemId = itemId;
        this.name = name;
        this.imageUrl = imageUrl;
        this.returnChecked = returnChecked;
        this.bagId = bagId;
        this.bagName = bagName;
    }

    public static ReturnCheckItemResponse of(BagItem item, Bag bag) {
        return new ReturnCheckItemResponse(
                item.getId(), item.getName(), item.getImageUrl(), item.isReturnChecked(),
                item.getBagId(), bag == null ? null : bag.getName());
    }
}
