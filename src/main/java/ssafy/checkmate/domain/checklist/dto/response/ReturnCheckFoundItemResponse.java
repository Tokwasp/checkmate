package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;

/**
 * 귀가 전 확인이 끝난 물품 한 줄 (카메라 화면의 '확인' 탭).
 *
 * 이미지를 함께 내리는 건 "이게 맞나" 눈으로 대조해야 하기 때문이다.
 * 33번이 방금 체크한 물품도 같은 타입으로 내려, 프론트가 확인 목록 맨 위에 그대로 끼워 넣는다.
 * 33-2번(손으로 체크·해제)도 같은 타입을 쓴다 — 해제한 줄은 returnChecked가 false로 나간다.
 */
@Getter
public class ReturnCheckFoundItemResponse {

    private final Long itemId;
    private final String name;
    /** 카메라로 인식해 담은 물품만 값이 있고, 손으로 체크한 물품은 null. */
    private final String imageUrl;
    /** 확인 목록에서는 항상 true(해제 응답만 false). 두 목록이 같은 컴포넌트를 쓰므로 값으로도 내린다. */
    private final boolean returnChecked;
    private final String bagName;

    private ReturnCheckFoundItemResponse(Long itemId, String name, String imageUrl, boolean returnChecked,
            String bagName) {
        this.itemId = itemId;
        this.name = name;
        this.imageUrl = imageUrl;
        this.returnChecked = returnChecked;
        this.bagName = bagName;
    }

    public static ReturnCheckFoundItemResponse of(BagItem item, Bag bag) {
        return new ReturnCheckFoundItemResponse(
                item.getId(), item.getName(), item.getImageUrl(), item.isReturnChecked(),
                bag == null ? null : bag.getName());
    }
}
