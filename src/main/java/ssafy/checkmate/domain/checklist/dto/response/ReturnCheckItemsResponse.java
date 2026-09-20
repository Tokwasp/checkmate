package ssafy.checkmate.domain.checklist.dto.response;

import java.util.List;
import lombok.Getter;

/**
 * 귀가 전 점검 물품 확인 현황(32번) — 카메라 화면 시트의 두 탭.
 *
 * 가방으로 묶지 않는다(31번과 다른 점). 화면이 체크 여부로만 가르기 때문이다.
 * 개수 두 개는 탭 라벨('확인 N' · '안 보임 M')이 각각 쓴다.
 */
@Getter
public class ReturnCheckItemsResponse {

    private final int checkedCount;
    private final int uncheckedCount;
    private final List<ReturnCheckFoundItemResponse> checkedItems;
    private final List<ReturnCheckMissingItemResponse> uncheckedItems;

    private ReturnCheckItemsResponse(int checkedCount, int uncheckedCount,
            List<ReturnCheckFoundItemResponse> checkedItems, List<ReturnCheckMissingItemResponse> uncheckedItems) {
        this.checkedCount = checkedCount;
        this.uncheckedCount = uncheckedCount;
        this.checkedItems = checkedItems;
        this.uncheckedItems = uncheckedItems;
    }

    public static ReturnCheckItemsResponse of(List<ReturnCheckFoundItemResponse> checkedItems,
            List<ReturnCheckMissingItemResponse> uncheckedItems) {
        return new ReturnCheckItemsResponse(checkedItems.size(), uncheckedItems.size(), checkedItems, uncheckedItems);
    }
}
